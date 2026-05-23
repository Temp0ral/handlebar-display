package com.motohud

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    // UI
    private lateinit var tvStatus: TextView
    private lateinit var tvInstruction: TextView
    private lateinit var tvStreet: TextView
    private lateinit var tvDistance: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnTest: Button

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
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvInstruction = findViewById(R.id.tvInstruction)
        tvStreet = findViewById(R.id.tvStreet)
        tvDistance = findViewById(R.id.tvDistance)
        btnConnect = findViewById(R.id.btnConnect)
        btnTest = findViewById(R.id.btnTest)

        // Request permissions
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            1
        )

        btnConnect.setOnClickListener {
            startScan()
        }

        btnTest.setOnClickListener {
            sendToDisplay("< Turn LEFT|Main St|0.3 mi")
        }
    }

    private fun startScan() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            android.util.Log.e("MotoHUD", "BLUETOOTH_SCAN permission not granted")
            return
        }

        android.util.Log.d("MotoHUD", "Starting BLE scan...")
        updateStatus("Scanning...", "#FFFF00")
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            android.util.Log.e("MotoHUD", "BLE scanner is null")
            updateStatus("BLE not available", "#FF0000")
            return
        }
        scanner.startScan(scanCallback)

        handler.postDelayed({
            scanner.stopScan(scanCallback)
            if (bluetoothGatt == null) {
                android.util.Log.e("MotoHUD", "Device not found after scan")
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
            tvStatus.setTextColor(android.graphics.Color.parseColor(color))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        bluetoothGatt?.close()
    }
}