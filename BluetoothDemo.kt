package mobility.lima.com.bibo

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mobility.lima.com.BuildConfig
import mobility.lima.com.NeedPermission
import mobility.lima.com.databinding.BluetoothDemoBinding
import mobility.lima.com.databinding.StopLayoutBinding
import java.nio.charset.Charset
import java.util.Locale
import java.util.UUID





internal var statusSendMessage: Boolean = true
@Suppress("DEPRECATION")
class BluetoothDemo : AppCompatActivity() {

    var time1: Long =0
    var time2: Long =0

    var scanStartTime: Long =0
    var scanStartConnectionTime: Long =0
    lateinit var btDevice: BluetoothDevice
    lateinit var scanRecord: ByteArray
    private val ioScope = CoroutineScope(Dispatchers.IO + Job())
    var statusAfterConnect: Boolean = true  // boolean validation :after connection

    private lateinit var contextNew: Context
    private var mBluetoothDeviceAddress: String? = null
    private var searchDevice: String = "BIBO 1.1 A"
    internal var util = Util()
    private var adapter: BluetoothAdapter? = null
    private var gatt: BluetoothGatt? = null
    private var tx: BluetoothGattCharacteristic? = null
    private var rx: BluetoothGattCharacteristic? = null
    private var settings: ScanSettings? = null
    private val scanFilters = ArrayList<ScanFilter>()
    private var mLEScanner: BluetoothLeScanner? = null

    // .............................................................................................
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            ioScope.launch {
                zedLogNew("vina1", "001 BluetoothGattCallback onConnectionStateChange")

                // Check for Bluetooth connect permission on Android 12 or higher
                if (ActivityCompat.checkSelfPermission(contextNew, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    return@launch
                }

                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> {





                        // Device is connected
                        when (status) {
                            BluetoothGatt.GATT_SUCCESS -> {
                                // Connection successful
                                zedLogNew("vina1", "001- Connection successful")

                            }
                            8 -> {
                                // Connection timeout error (GATT_CONN_TIMEOUT)
                                zedLogNew("vina1", "001- Connection timeout error (GATT_CONN_TIMEOUT)")
                            }
                            22 -> {
                                // Disconnected by local host (GATT_CONN_TERMINATE_LOCAL_HOST)
                                zedLogNew("vina1", "001- Disconnected by local host (GATT_CONN_TERMINATE_LOCAL_HOST) ")
                            }
                            19 -> {
                                // Disconnected by remote device or user (GATT_CONN_TERMINATE_PEER_USER)
                                zedLogNew("vina1", "001- Disconnected by remote device or user (GATT_CONN_TERMINATE_PEER_USER)")
                            }
                            else -> {
                                // Handle other connection errors
                                zedLogNew("vina1", "001- Handle other connection errors")
                            }
                        }






                        statusAfterConnect = true // connected
                        zedLogNew("vina1", "001- $searchDevice is connected...")
                        // Add a delay before starting service discovery
                      //  delay(2500) // Adjust the delay time as needed

                        // Discover services.
                        if (!gatt.discoverServices()) {
                            zedLogNew("vina1", "001- Failed to start discovering services!")
                        } else {
                            // .....................................
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                zedLogNew("vina1", "002- Service discovery completed!")
                            } else {
                                zedLogNew("vina1", "002- Service discovery failed with status: $status")
                            }
                            // .....................................
                        }

                    }
                    BluetoothGatt.STATE_DISCONNECTED -> {


                        // Handle the disconnection reason
                        handleDisconnectReason(gatt.device.address, status)


                        statusAfterConnect = false // dis-connected
                        zedLogNew("vina1", "001- BIBO bluetooth available  is dis-connected...")
                        zedLogNew("vina1", "................................................")



                        if (gatt != null && mBluetoothDeviceAddress != null && statusSendMessage) {
                            // Attempt to connect to the device
                            if (gatt.connect()) {
                                zedLogNew("scanning", "01 *********************")
                            } else {
                                zedLogNew("scanning", "02")
                            }
                        }


                    }
                    else -> {
                        zedLogNew("vina1", "Connection state changed. New state: $newState")
                    }
                }
            }
        }

        fun handleDisconnectReason(deviceAddress: String, status: Int) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    // Handle successful disconnection (e.g., user-initiated)
                    zedLogNew("vina1", "Device $deviceAddress disconnected (Success)")
                }
                8 -> {
                    // Handle connection timeout
                    zedLogNew("vina1", "Device $deviceAddress disconnected (Connection Timeout)")
                }
                1 -> {
                    // Handle disconnection initiated by the remote device or user
                    zedLogNew("vina1", "Device $deviceAddress disconnected (Peer/User Terminate)")
                }
                22 -> {
                    // Handle disconnection initiated by the local host
                    zedLogNew("vina1", "Device $deviceAddress disconnected (Local Host Terminate)")
                }
                else -> {
                    // Handle other disconnection reasons
                    zedLogNew("vina1", "Device $deviceAddress disconnected (Unknown Reason: $status)")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            zedLogNew("vina1", "002 BluetoothGattCallback onServicesDiscovered")

            // Check for Bluetooth connect permission on Android 12 or higher
            if (ActivityCompat.checkSelfPermission(contextNew, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                zedLogNew("vina1", "002- Service discovery completed!")
            } else {
                zedLogNew("vina1", "002- Service discovery failed with status: $status")
            }

            // Use a coroutine for potentially time-consuming operations
            ioScope.launch {
                try {
                    tx = gatt.getService(UART_UUID).getCharacteristic(TX_UUID)
                    rx = gatt.getService(UART_UUID).getCharacteristic(RX_UUID)

                    if (!gatt.setCharacteristicNotification(rx, true)) {
                        zedLogNew("vina1", "002- setCharacteristicNotification???")
                    }

                    if (rx!!.getDescriptor(CLIENT_UUID) != null) {
                        val desc = rx!!.getDescriptor(CLIENT_UUID)
                        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        if (!gatt.writeDescriptor(desc)) {
                            zedLogNew("vina1", "002- writeDescriptor")
                        }
                    }

                    // Add any other asynchronous operations here
                } catch (e: Exception) {
                    zedLogNew("vina1", "002- catch : " + e.message)
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)
            zedLogNew("vina1", "003..")
            if (statusSendMessage) {
                zedLogNew("vina1", "acknowledgement confirmed ${characteristic.getStringValue(0)}  :: ${SmartService().getTime()}")
                sendSingleMessage("201001")
            }
        }
    }
// .............................................................................................

    lateinit var binding: BluetoothDemoBinding

    //..............................................................................................on create
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BluetoothDemoBinding.inflate(layoutInflater)
        val mView = binding.root
        setContentView(mView)
        zedLogNew("vina1", "on create")

        // check permission
        try {
            val nextScreen1 = Intent(applicationContext, NeedPermission::class.java)
            nextScreen1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(nextScreen1)
        } catch (e: Exception) {
            zedLogNew("vina1", "catch permission ${e.message}")
        }


        contextNew = applicationContext

        val adapterBlu = contextNew.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = adapterBlu.adapter


        binding.label1.setOnClickListener {
            statusSendMessage = true
            time1 = 0
            time2 = 0
            zedLogNew("vina1","")
            zedLogNew("vina1","")
            zedLogNew("vina1","")
            zedLogNew("vina1","")
            zedLogNew("vina1","")
            zedLogNew("vina1","")
            zedLogNew("vina1", "********************************************************************** start ${SmartService().getTime()}")
            scanDevice(true) //on create
        }

        binding.label.setOnClickListener {
            statusSendMessage = true
            time1 = 0
            time2 = 0
            zedLogNew("vina1","")
            zedLogNew("vina1","")
            zedLogNew("vina1","")
            zedLogNew("vina1","")
            zedLogNew("vina1","")
            zedLogNew("vina1","")
            zedLogNew("vina1", "********************************************************************** connect ${SmartService().getTime()}")
            // Call the 'readyToConnectDevice' function with the discovered device and scan record
            readyToConnectDevice(btDevice, scanRecord) // high Scan Callback
        }

    }

    // high Scan Callback
    private val highScanCallback =
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                try {
                    // Check if the app has Bluetooth connect permission on Android 12 or higher
                    if (ActivityCompat.checkSelfPermission(contextNew, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        return
                    }


                    // Log that scanning is in progress
                    zedLogNew("vina1", "scanning...")

                    // Check if the result has a non-null device name and address
                    if (result.device.name != null && result.device.address != null) {
                        // Check if the discovered device matches the 'searchDevice'
                        if (searchDevice == result.device.name.toString()) {
                            // Stop scanning to avoid further device discovery
                            scanDevice(false)

                            // Record the end time for scan
                            val scanEndTime = System.currentTimeMillis()
                            // Calculate the time taken for scanning
                            time1 = scanEndTime - scanStartTime
                            // Log the scan time
                            zedLogNew("vina1", "**** Scan & Find Time: $time1 ms ****")


                            // Get the Bluetooth device and scan record
                            btDevice = result.device
                            scanRecord = result.scanRecord!!.bytes

                            // Log that the device is ready to connect
                            zedLogNew("vina1", "$searchDevice : ready to connect")

                            // Call the 'readyToConnectDevice' function with the discovered device and scan record
                            readyToConnectDevice(btDevice, scanRecord) // high Scan Callback
                        }
                    } else {
                        zedLogNew("vina1", "name or address is null")
                    }
                } catch (e: Exception) {
                    // Handle any exceptions that occur during scanning
                    zedLogNew("vina1", "onScanResult catch:" + e.message.toString())
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                // Log scan failure with the error code
                zedLogNew("vina1", "onScanFailed : $errorCode")
            }
        }

    fun scanDevice(enable: Boolean) {
        // Record the start time for scan
        scanStartTime = System.currentTimeMillis()

        // Check if the app has Bluetooth scan permission on Android 12 or higher
        if (ActivityCompat.checkSelfPermission(contextNew, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return
        }
/*

        mLEScanner = adapter?.bluetoothLeScanner

        settings = ScanSettings.Builder()
            .build()
*/
        mLEScanner = adapter?.bluetoothLeScanner

        settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // or other suitable mode
            .setReportDelay(0) // Remove any delay to report scan results immediately
            .build()
            //minimize the scanning time
            gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)


        if (mLEScanner == null) {
            zedLogNew("vina1", "BluetoothLeScanner is null")
            return
        }

        if (enable) {
            try {
                // Start the high-priority scan using the highScanCallback
                zedLogNew("vina1", "Starting scan...")
                mLEScanner?.startScan(scanFilters, settings, highScanCallback)
            } catch (e: Exception) {
                // Handle any exceptions that occur during scan initialization or stopping
                zedLogNew("vina1", "scan error: ${e.message}")
            }
        }
        else {
            zedLogNew("vina1", "Stopping scan...")
            mLEScanner?.stopScan(highScanCallback)
        }
    }
    // Declare a variable to hold the reference to the active connection job
    var activeConnectionJob: Job? = null

    // Declare a variable to track the connection status
    var isConnecting = false
    private var lastConnectionAttemptTime: Long = 0
    private val connectionAttemptInterval: Long = 2000 // 2 seconds

    fun readyToConnectDevice(bluetoothDevice: BluetoothDevice, bytes: ByteArray) {
        // Check if the app has Bluetooth connect permission on Android 12 or higher
        if (ActivityCompat.checkSelfPermission(contextNew, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return
        }

        scanStartConnectionTime = System.currentTimeMillis()



        // Cancel the previous connection attempt if it exists
        activeConnectionJob?.cancel()



        val currentTime = System.currentTimeMillis()
        // Check if enough time has passed since the last connection attempt
        if (currentTime - lastConnectionAttemptTime < connectionAttemptInterval) {
            zedLogNew("vina1", "connect to gatt IGNORE (Within 2 seconds)")
            return
        }




        // Check if a connection attempt is already in progress
       /* if (isConnecting) {
            zedLogNew("vina1", "connect to gatt IGNORE")
            return
        }
        else{
            zedLogNew("vina1", "connect to gatt init ${SmartService().getTime()}")
        }
*/

        // Set the connecting flag to true to prevent concurrent connections
        isConnecting = true

        // Use a coroutine to connect to the BluetoothGatt
        activeConnectionJob = ioScope.launch(Dispatchers.IO) {
            try {
                scanDevice(false) // init

                // Create a new connection (not a reconnection)
                zedLogNew("vina1", "connect to gatt")
                mBluetoothDeviceAddress = bluetoothDevice.address
                gatt = bluetoothDevice.connectGatt(contextNew, false, callback)
            } catch (e: Exception) {
                zedLogNew("vina1", "** PROBLEM!!!!  connectGatt error: ${e.message}")
            }
            finally {
                // Reset the connecting flag to allow new connections
                isConnecting = false

                // Update the last connection attempt time
                lastConnectionAttemptTime = System.currentTimeMillis()
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        scanDevice(false) // onDestroy : stop scan
        ioScope.launch {
            disconnectAndCloseGatt(gatt) // on destroy
        }
        //disconnect and close the connection
        zedLogNew("vina1", "on destroy")
    }

    companion object {
        var UART_UUID   = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")!!// UUIDs for UAT service and associated characteristics.
        var TX_UUID     = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")!!
        var RX_UUID     = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")!!
        var CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")!!// UUID for the BTLE client characteristic which is necessary for notifications.
    }


    //..............................................................................................send false message
    fun sendSingleMessage(message: String) {
        scanDevice(false) // sen init new01

        ioScope.launch {

            // Check if the app has Bluetooth connect permission on Android 12 or higher
            if (ActivityCompat.checkSelfPermission(contextNew, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return@launch
            }

            // Check if 'tx' or the message is empty
            if (tx == null || message.isEmpty()) {
                zedLogNew("vina1", "send message $message: failed do nothing : there is no device or message to send.")
                return@launch
            }

            // Set the value of 'tx' to the message as bytes in UTF-8 encoding
            tx!!.value = message.toByteArray(Charset.forName("UTF-8"))
            tx!!.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE



            // Attempt to write the characteristic 'tx' with the message
            if (gatt!!.writeCharacteristic(tx)) {
                // Writing was successful
                statusSendMessage = false
                disconnectAndCloseGatt(gatt) // send data
                zedLogNew("vina1", "-------------------->sent: $message :${SmartService().getTime()}")
                zedLogNew("vina1", "")
                zedLogNew("vina1", "")
                zedLogNew("vina1", "")
                zedLogNew("vina1", "")
                zedLogNew("vina1", "")
                zedLogNew("vina1", "")
                zedLogNew("vina1", "")
                zedLogNew("vina1", "")


                // Record the end time for scan
                val scanEndTime = System.currentTimeMillis()
                // Calculate the time taken for scanning
                time2 = scanEndTime - scanStartConnectionTime
                // Log the scan time
              //  zedLogNew("vina1", "**** Connect & Sent: $time2 ms ****")
                zedLogNew("vina1", "********************************************************************** end")


            } else {
                // Writing failed, log an error and possibly finish the operation
                zedLogNew("vina1", "Couldn't write TX characteristic!")
                finish() // You might want to replace this with appropriate error handling
                zedLogNew("vina1", "********************************************************************** end")

            }
        }
    }

    private fun disconnectAndCloseGatt(gatt: BluetoothGatt?) {
        if (ActivityCompat.checkSelfPermission(contextNew, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return
        }

        scanDevice(false) // disconnect new01


        gatt?.let {
            try {
                // Disconnect the BluetoothGatt
                it.disconnect()
                zedLogNew("vina1", "--------------------> disconnect success")
            } catch (e: Exception) {
                // Handle any exceptions that occur during disconnection
                zedLogNew("vina1", "--------------------> disconnect : catch : ${e.message}")
            }

            try {
                // Close the BluetoothGatt
                it.close()
                zedLogNew("vina1", "--------------------> gatt close success")
            } catch (e: Exception) {
                // Handle any exceptions that occur during closing
                zedLogNew("vina1", "--------------------> gatt close : catch : ${e.message}")
            }


        } ?: run {
            zedLogNew("vina1", "--------------------> gatt is null")
        }



    }

    fun zedLogNew(tag: String, message: String) {

        if (BuildConfig.DEBUG) {
            if (message.trim().lowercase(Locale.ENGLISH).contains("start") ||
                message.trim().lowercase(Locale.ENGLISH).contains("success") ||
                message.trim().lowercase(Locale.ENGLISH).contains("stop") ||
                message.trim().lowercase(Locale.ENGLISH).contains("total") ||
                message.trim().lowercase(Locale.ENGLISH).contains("acknowledgement") ||
                message.trim().lowercase(Locale.ENGLISH).contains("startMqtt") ||
                message.trim().lowercase(Locale.ENGLISH).contains("send") ||
                message.trim().lowercase(Locale.ENGLISH).contains("start scan")) {
                Log.i(tag, message)
            }
            else {
                Log.d(tag, message)
            }
        }



    }

}
