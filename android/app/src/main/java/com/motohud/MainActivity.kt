package com.motohud

import android.graphics.Color
import android.util.Log
import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.*
import java.util.*

class MainActivity : AppCompatActivity() {

    // UI
    private lateinit var tvStatus: TextView
    private lateinit var tvInstruction: TextView
    private lateinit var tvStreet: TextView
    private lateinit var tvDistance: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnTest: Button
    private lateinit var etSearch: EditText
    private lateinit var lvSuggestions: ListView

    // BLE
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val CHAR_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    private val DEVICE_NAME = "MotoHUD"

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)

    // Navigation
    private lateinit var navManager: NavigationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private lateinit var placesClient: PlacesClient

    // Flag to prevent text watcher firing when we set text after selection
    private var isSelectingFromList = false

    private var testIndex = 0
    private val testInstructions = listOf(
        "LEFT|Test Street|0.3 mi",
        "RIGHT|Test Street|0.3 mi",
        "STRAIGHT|Test Street|0.3 mi",
        "UTURN|Test Street|0.3 mi",
        "ARRIVE|Test Street|0.3 mi"
    )

    // BLE Scan callback
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) return
            if (device.name == DEVICE_NAME) {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
                updateStatus("Connecting...", "#FFFF00")
                device.connectGatt(this@MainActivity, false, gattCallback)
            }
        }
    }

    // GATT callback
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) return
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                bluetoothGatt = null
                writeCharacteristic = null
                updateStatus("BLE: Disconnected", "#FF0000")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SERVICE_UUID)
            writeCharacteristic = service?.getCharacteristic(CHAR_UUID)
            bluetoothGatt = gatt
            updateStatus("BLE: Connected", "#00FF00")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = com.motohud.databinding.ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tvStatus = binding.tvStatus
        tvInstruction = binding.tvInstruction
        tvStreet = binding.tvStreet
        tvDistance = binding.tvDistance
        btnConnect = binding.btnConnect
        btnTest = binding.btnTest
        etSearch = binding.etSearch
        lvSuggestions = binding.lvSuggestions

        navManager = NavigationManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ),
            1
        )

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }
        placesClient = Places.createClient(this)

        val token = AutocompleteSessionToken.newInstance()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isSelectingFromList) return
                val query = s.toString()
                if (query.trim().length < 3) {
                    lvSuggestions.visibility = View.GONE
                    return
                }
                val request = FindAutocompletePredictionsRequest.builder()
                    .setSessionToken(token)
                    .setQuery(query)
                    .build()

                placesClient.findAutocompletePredictions(request)
                    .addOnSuccessListener { response ->
                        val suggestions = response.autocompletePredictions.map {
                            it.getFullText(null).toString()
                        }
                        val adapter = ArrayAdapter(
                            this@MainActivity,
                            android.R.layout.simple_list_item_1,
                            suggestions
                        )
                        lvSuggestions.adapter = adapter
                        lvSuggestions.visibility = View.VISIBLE
                    }
                    .addOnFailureListener { e ->
                        Log.e("MotoHUD", "Autocomplete error: ${e.message}")
                        lvSuggestions.visibility = View.GONE
                    }
            }
        })

        lvSuggestions.setOnItemClickListener { _, _, position, _ ->
            val selected = lvSuggestions.adapter.getItem(position).toString()
            isSelectingFromList = true
            etSearch.setText(selected)
            etSearch.setSelection(selected.length)
            isSelectingFromList = false
            lvSuggestions.adapter = null
            lvSuggestions.visibility = View.GONE
            startNavigation(selected)
        }

        btnConnect.setOnClickListener { startScan() }

        btnTest.setOnClickListener {
            sendToDisplay(testInstructions[testIndex])
            testIndex = (testIndex + 1) % testInstructions.size
        }
    }

    private fun startNavigation(destination: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        updateStatus("Getting route...", "#FFFF00")

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                scope.launch {
                    val success = navManager.getRoute(destination, it.latitude, it.longitude)
                    if (success) {
                        updateStatus("Navigating", "#00FF00")
                        startLocationUpdates()
                    } else {
                        updateStatus("Route failed", "#FF0000")
                    }
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val step = navManager.updateLocation(location)
                    step?.let {
                        val display = navManager.formatForDisplay(it)
                        sendToDisplay(display)
                        updateNavUI(it)
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    private fun updateNavUI(step: NavigationManager.NavStep) {
        handler.post {
            tvInstruction.text = step.instruction
            tvStreet.text = step.street
            tvDistance.text = "${step.distanceMeters}m"
        }
    }

    private fun startScan() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("MotoHUD", "BLUETOOTH_SCAN permission not granted")
            return
        }

        Log.d("MotoHUD", "Starting BLE scan...")
        updateStatus("Scanning...", "#FFFF00")
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.e("MotoHUD", "BLE scanner is null")
            updateStatus("BLE not available", "#FF0000")
            return
        }
        scanner.startScan(scanCallback)

        handler.postDelayed({
            scanner.stopScan(scanCallback)
            if (bluetoothGatt == null) {
                updateStatus("Device not found", "#FF0000")
            }
        }, 10000)
    }

    fun sendToDisplay(data: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        writeCharacteristic?.let { char ->
            char.value = data.toByteArray()
            bluetoothGatt?.writeCharacteristic(char)
        }
    }

    private fun updateStatus(msg: String, color: String) {
        handler.post {
            tvStatus.text = msg
            tvStatus.setTextColor(Color.parseColor(color))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        scope.cancel()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        bluetoothGatt?.close()
    }
}