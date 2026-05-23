package com.motohud

import android.content.Context
import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class NavigationManager(private val context: Context) {

    private val apiKey = "AIzaSyCAj6Fmj5HOgO9FiX0DGcX1gHex_SAuihw"
    private var steps = mutableListOf<NavStep>()
    private var currentStepIndex = 0

    data class NavStep(
        val instruction: String,
        val street: String,
        val distanceMeters: Int,
        val endLat: Double,
        val endLng: Double
    )

    suspend fun getRoute(destination: String, originLat: Double, originLng: Double): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                        "origin=$originLat,$originLng" +
                        "&destination=${destination.replace(" ", "+")}" +
                        "&key=$apiKey"

                val response = URL(url).readText()
                val json = JSONObject(response)

                if (json.getString("status") != "OK") return@withContext false

                steps.clear()
                currentStepIndex = 0

                val route = json.getJSONArray("routes").getJSONObject(0)
                val leg = route.getJSONArray("legs").getJSONObject(0)
                val stepsArray = leg.getJSONArray("steps")

                for (i in 0 until stepsArray.length()) {
                    val step = stepsArray.getJSONObject(i)
                    val instruction = stripHtml(step.getString("html_instructions"))
                    val distance = step.getJSONObject("distance").getInt("value")
                    val endLocation = step.getJSONObject("end_location")
                    val streetName = extractStreetName(instruction)

                    steps.add(NavStep(
                        instruction = instruction,
                        street = streetName,
                        distanceMeters = distance,
                        endLat = endLocation.getDouble("lat"),
                        endLng = endLocation.getDouble("lng")
                    ))
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    fun updateLocation(location: Location): NavStep? {
        if (steps.isEmpty() || currentStepIndex >= steps.size) return null

        val currentStep = steps[currentStepIndex]
        val stepEnd = Location("").apply {
            latitude = currentStep.endLat
            longitude = currentStep.endLng
        }

        val distanceToEnd = location.distanceTo(stepEnd)

        // Advance to next step if within 30 meters
        if (distanceToEnd < 30 && currentStepIndex < steps.size - 1) {
            currentStepIndex++
        }

        return steps[currentStepIndex]
    }

    fun getCurrentStep(): NavStep? {
        return if (steps.isNotEmpty()) steps[currentStepIndex] else null
    }

    fun formatForDisplay(step: NavStep): String {
        val arrow = when {
            step.instruction.contains("left", ignoreCase = true) -> "<"
            step.instruction.contains("right", ignoreCase = true) -> ">"
            step.instruction.contains("straight", ignoreCase = true) -> "^"
            step.instruction.contains("arrive", ignoreCase = true) -> "*"
            else -> "-"
        }
        val distance = if (step.distanceMeters > 1000) {
            "${"%.1f".format(step.distanceMeters / 1000.0)} km"
        } else {
            "${step.distanceMeters} m"
        }
        return "$arrow ${step.instruction}|${step.street}|$distance"
    }

    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), "").trim()
    }

    private fun extractStreetName(instruction: String): String {
        // Remove everything after "toward"
        val cleaned = instruction.replace(Regex("\\s+toward.*", RegexOption.IGNORE_CASE), "").trim()
        // Extract street after "on", "onto", "to", "Turn right on" etc
        val patterns = listOf(" on ", " onto ", " to ")
        for (pattern in patterns) {
            val idx = cleaned.indexOf(pattern, ignoreCase = true)
            if (idx >= 0) return cleaned.substring(idx + pattern.length).trim()
        }
        // If no pattern found just return cleaned string minus any leading direction words
        return cleaned
            .replace(Regex("^(turn left|turn right|continue|head|merge|take)", RegexOption.IGNORE_CASE), "")
            .trim()
    }
}