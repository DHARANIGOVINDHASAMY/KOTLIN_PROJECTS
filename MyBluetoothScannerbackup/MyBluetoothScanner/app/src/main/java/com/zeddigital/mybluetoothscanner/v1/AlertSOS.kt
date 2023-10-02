package com.zeddigital.mybluetoothscanner.v1

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Geocoder
import android.os.*
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task

import com.zeddigital.mybluetoothscanner.databinding.V1LayoutBinding
import org.json.JSONException
import org.json.JSONObject

import java.nio.charset.Charset
import java.util.*
import java.util.regex.Pattern
internal var util = Util()



class AlertSOS : AppCompatActivity() {
     var dialogMessageAlert =""
     var dialogMacAddress =""
     var stopRequestAddress =""



    private var handler: Handler = Handler(Looper.myLooper()!!)
    var runnable: Runnable? = null



    private var confirmDialog: Dialog? = null

    private var timer: CountDownTimer? = null

    private var sb = StringBuilder()

    var statusAfterConnect  :  Boolean = true  // boolean validation :after connection
    var statusSendMessage  :  Boolean = true

    //var count =1

    private var pageStatusBooleanCheck =true // activity is live or not

    var statusBindLocationUI =false


    //..................................................................
    //gps
    var gpsLatitude   : String ="0.0"
    var gpsLongitute  : String= "0.0"
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    //..................................................................

    private lateinit var contextNew: Context

    var location: Task<*>? = null


    var availableCountCheck=1


    private var mBluetoothDeviceAddress: String? = null


    // beacon details
    private var beaconCommand = ""
    private var beaconMacAddress: String =""
    private var searchDevice   : String= "BIBO 1.1 O"
    private var searchDeviceC   : String= "BIBO 1.1 C"
    private var searchDeviceA   : String= "BIBO 1.1 A"
    //true

  //  private var rippleBackground: RippleBackground? = null




//    var statusAfterConnect2  :  Boolean = true  // callback2





//    var statusConnect=true // boolean validation : scan

    // .............................................................................................
// BTLE state
    private var adapter: BluetoothAdapter?       = null
    private var gatt: BluetoothGatt?             = null
    private var tx: BluetoothGattCharacteristic? = null
    private var rx: BluetoothGattCharacteristic? = null

    //for Scanning Ble Device in api version above 21
    private var settings: ScanSettings?         = null
    //private var filters: List<ScanFilter>?      = null
    private val scanFilters = ArrayList<ScanFilter>()

    var dialog: Dialog? = null


    private var mLEScanner: BluetoothLeScanner? = null
    // ble permission
    //private var requestCodeBle: Int = 1

    /*override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == requestCodeBle){
            if (resultCode == RESULT_OK){

                mLEScanner = adapter!!.bluetoothLeScanner
                settings = ScanSettings.Builder()
                        .build()
                scanDeviceSOS(true, contextNew) // onActivityResult

            }
        }
    }*/

    // .............................................................................................
    private val callback = object : BluetoothGattCallback() {


        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                statusAfterConnect = true  // connected

                zedLogNew("vina1", "b7- $searchDevice is connected...")


                availableCountCheck = 1
                runOnUiThread {
                    try {
                        if (!isFinishing) {
                            zedLogNew("scan3", "b7- BIBO bluetooth available is connected...")
                        }
                    } catch (e: Exception) {
                        zedLogNew("vina1", "b7- error : STATE_CONNECTED : " + e.message.toString())

                    }
                }
                // Discover services.
                if (ActivityCompat.checkSelfPermission(
                        contextNew,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ) {
                    return
                }
                if (!gatt.discoverServices()) {
                    zedLogNew("vina1", "Failed to start discovering services!")
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                statusAfterConnect = false  //  dis-connected

                zedLogNew("vina1", "b7 - BIBO bluetooth available  is dis-connected...")

                scanDevice(true)//disconnect

            } else {
                zedLogNew("vina1", "Connection state changed.  New state: $newState")
            }
        }


        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                zedLogNew("vina1", "Service discovery completed!")
            } else {
                zedLogNew("vina1", "Service discovery failed with status: $status")
            }
            try {
                tx = gatt.getService(UART_UUID).getCharacteristic(TX_UUID)
                rx = gatt.getService(UART_UUID).getCharacteristic(RX_UUID)
                if (ActivityCompat.checkSelfPermission(
                        contextNew,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ) {
                    return
                }
                if (!gatt.setCharacteristicNotification(rx, true)) {
                    Log.d("callback","01")
                }
                if (rx!!.getDescriptor(CLIENT_UUID) != null) {
                    val desc = rx!!.getDescriptor(CLIENT_UUID)
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    if (!gatt.writeDescriptor(desc)) {
                        Log.d("callback","02")
                    }
                }
            } catch (e: Exception) {
                zedLogNew("callback", "b7- tx/rx : " + e.message)
            }

        }


        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)


            // zedLogNew("scan3","01-bluetooth available...$availableCountCheck")
            // availableCountCheck++


//!characteristic.getStringValue(0).contains("WiFi") && !characteristic.getStringValue(0).contains("false") && !characteristic.getStringValue(0).contains("true")
            if (characteristic.getStringValue(0).isNotEmpty() && characteristic.getStringValue(0).contains("#")) {
//                sendSingleMessage("sos3")



                if(statusAfterConnect) {
                    statusAfterConnect = false
                    zedLogNew("vina1", "b8- -bluetooth available - after connect.")
                    zedLogNew("vina1", "b8- verify device is :::::::${characteristic.getStringValue(0)}")


                    val pattern = Pattern.compile("^(\\w)#(.{17})#$")
                    val matcher = pattern.matcher(characteristic.getStringValue(0))


                    if (matcher.find()) {
                        try {
                            beaconCommand = matcher.group(1)!!.toString()
                            beaconMacAddress = matcher.group(2)!!.toString()
                            zedLogNew("vina1", "b8--beacon:$beaconCommand & address:$beaconMacAddress")

                        } catch (e: Exception) {
                            zedLogNew("vina1", "b8-verify device is failed : 2021:${e.message}")

                            //*** disconnect & re scan
                        }

                        runOnUiThread {
                            //................................................. peripheral device name check
                            if (beaconCommand == "O"||beaconCommand == "C" ||beaconCommand == "A") {

                                /*doAsync {
                                    if (seatDatabase!!.MacDao().checkMacAlreadyExist(beaconMacAddress) >= 1) {

                                    } else {
                                        try {
                                            zedLogNew("vina1", "b8-Error code: 2123")
                                        } catch (e: Exception) {
                                        }
                                    }
                                    //.....................................
                                }*/

                                //https://stackoverflow.com/questions/68268194/what-do-i-use-now-that-anko-is-deprecated

                                if(statusSendMessage){
                                    sendSingleMessage("301")
                                }
                                else{
                                    Log.d("shuttle03","duplicate signal")
                                }


                                /*try {
                                    Thread.sleep(4000)
                                    disconnectNew(gatt, "b8-gatt is disconnected : on illegal. ")
                                } catch (e: Exception) {
                                    zedLogNew("vina1", "b8-illegal gatt failed : " + e.message)
                                }finally {
                                    finish()
                                }*/


                            } else {
                                zedLogNew("vina1", "b8--mismatch-$matcher")
                            }


                        }


                    }
                    else{
                        zedLogNew("vina1", "b8-not match..")
                    }

                }
                else{
                    zedLogNew("vina1", "..")
                }

            }
        }
    }
// .............................................................................................


    /*override fun onPause() {
        super.onPause()
    }*/
    lateinit var binding: V1LayoutBinding
    //..............................................................................................on create
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding= V1LayoutBinding.inflate(layoutInflater)
        val mView = binding.root
        setContentView(mView)


        // Check feature availability at runtime : Check to see if the BLE feature is available.
        packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }?.also {
            finish()
        }

        locationFetch() // new

        contextNew=applicationContext
        //..............................................  room config

        /*val tempLocationSharedPreference =  getSharedPreferences("bluetoothTemp", Context.MODE_PRIVATE)
        zedLogNew("vina1","temp location: -->"+tempLocationSharedPreference.getString("gpsLat","0.0")+","+tempLocationSharedPreference.getString("gpsLong","0.0"))
        if(tempLocationSharedPreference.getString("gpsLat","0.0")!="0.0"){
            gpsLatitude= tempLocationSharedPreference.getString("gpsLat","0.0")!!.toString()
            gpsLongitute= tempLocationSharedPreference.getString("gpsLong","0.0")!!.toString()
        }*/


        gpsLatitude= "0.0"
        gpsLongitute= "0.0"

        bindLocationUI() // display user location on ui (api / geo code) : on create


        val adapterBlu = contextNew.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = adapterBlu.adapter
        //............................................... bluetooth config start
        //adapter = BluetoothAdapter.getDefaultAdapter()


        //............................................... bluetooth config end

        //rippleBackground!!.startRippleAnimation()  // start


        timer = object : CountDownTimer(20000, 1) {
            override fun onFinish() {


                onBackPressed()

                //dialogBox()



            }
            override fun onTick(millisUntilFinished: Long) {
                // millisUntilFinished    The amount of time until finished.
            }
        }.start()



        //....................................................................................
        Log.d("d01","intent1 on create")



        try{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    &&ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED
                    &&ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                    launchStopRequestHigher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_ADVERTISE,Manifest.permission.BLUETOOTH_CONNECT))
                }else {
                    //ble enabled
                    mLEScanner = adapter!!.bluetoothLeScanner
                    settings = ScanSettings.Builder()
                        .build()
                    scanDeviceSOS(applicationContext) //on create
                }
            }
            else {
                try {
                    if (adapter != null) {
                        if (!adapter!!.isEnabled) {


                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            launchStopRequest.launch(enableBtIntent)


                        } else {
                            //ble enabled
                            mLEScanner = adapter!!.bluetoothLeScanner
                            settings = ScanSettings.Builder()
                                .build()
                            scanDeviceSOS(applicationContext) //on create
                        }
                    } else {
                        finish()
                    }
                }catch (e:Exception){
                    finish()
                }
            }
        }catch (e: Exception){
            finish()
        }



    }




    //* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *



    //............................................................................For API above 21
    // high Scan Callback
    private val highScanCallback =
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {

                try{
                    zedLogNew("vina1","name & address...")
                    if (ActivityCompat.checkSelfPermission(
                            contextNew,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ) {
                        return
                    }
                    if (result.device.name != null && result.device.address != null){


                        if (searchDevice == result.device.name.toString()||searchDeviceC == result.device.name.toString() ||searchDeviceA == result.device.name.toString()) {
                            scanDevice(false) // highScanCallback


                            val btDevice = result.device
                            val scanRecord    = result.scanRecord!!.bytes

                            // currentLocationCall(result.device.address,"true")// high Scan Callback

                            zedLogNew("scan3","name & address : ready to connect")
                            readyToConnectDevice(btDevice,scanRecord) // high Scan Callback
                        }






                    }else{
                        zedLogNew("vina1","name or address is null")
                    }
                }
                catch (e :Exception){
                    zedLogNew("vina1", "name or address high catch:"+e.message.toString())
                }
            }
            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                zedLogNew("vina1", "name or address 002:$errorCode")
            }
        }


    //..............................................................................................scan & stop
    private fun scanDeviceSOS(context: Context) {
        contextNew=context
        zedLogNew("vina1","b1-scan init")
        zedLogNew("scanning","b2-scan scan state start")
        if(mLEScanner!=null){ //mLEScanner
            //  progress_bar.visibility=View.GONE
            zedLogNew("scanning","b3-scan scan mode high")

            try{
                if (ActivityCompat.checkSelfPermission(
                        contextNew,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ) {
                    return
                }
                mLEScanner!!.startScan(scanFilters, settings, highScanCallback)
            }catch (e2 : Exception){
                zedLogNew("scanning","b3-scan scan mode high catch ${e2.message}")
            }

        }
    }

    //..............................................................................................scan & stop
    fun scanDevice(enable: Boolean) {
        zedLogNew("scanning","new b4-scan init")

        if (enable) {


            // bleStatus = false

            zedLogNew("scanning","new b4-scan start")
            zedLogNew("scanning","new b4-scan mode high init ")
            if(mLEScanner!=null){ //mLEScanner
                //  progress_bar.visibility=View.GONE
                zedLogNew("scanning","new b4-scan mode high ready ")

                try{
                    if (ActivityCompat.checkSelfPermission(
                            contextNew,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ) {
                        return
                    }
                    mLEScanner!!.startScan(scanFilters, settings, highScanCallback)
                }catch (e2 : Exception){
                    zedLogNew("scan1","new b4-scan mode high  try-002${e2.message}")
                }
            }


        }
        else {
            if(mLEScanner!=null){
                try{
                    zedLogNew("scanning","b4-scan stop high")
                    mLEScanner!!.stopScan(highScanCallback) // BT Adapter is not turned ON
                }catch (e2 : Exception){
                    zedLogNew("scan1","b4-scan stop high 01${e2.message}")
                }
            }
        }










    }
    //..............................................................................................ready connecting to device
    fun readyToConnectDevice(bluetoothDevice: BluetoothDevice, bytes: ByteArray) {

        scanDevice(false) //readyToConnectDevice

        if (timer != null) {
            timer!!.cancel()
            zedLogNew("scanning","b5 timer canceled")
        }


        if (ActivityCompat.checkSelfPermission(
                contextNew,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            return
        }
        if (searchDevice == bluetoothDevice.name.toString()||searchDeviceC == bluetoothDevice.name.toString() ||searchDeviceA == bluetoothDevice.name.toString()) {


            if (util.parseUUIDs(bytes).contains(UART_UUID)) {


                try {
                    // Previously connected device.  Try to reconnect.
                    if (bluetoothDevice.address == mBluetoothDeviceAddress && gatt != null && mBluetoothDeviceAddress != null) {
                        if (gatt!!.connect()) {
                            zedLogNew("scanning", "01")
                        } else {
                            zedLogNew("scanning", "02")
                        }
                    } else {
                        zedLogNew("scanning", "03")
                        gatt = bluetoothDevice.connectGatt(contextNew, false, callback)
                        mBluetoothDeviceAddress = bluetoothDevice.address
                        dialogMacAddress = mBluetoothDeviceAddress.toString()
                    }
                }
                catch (e: Exception) {
                    zedLogNew("scanning","b5 try-"+e.message)
                }

            }
            else{
                zedLogNew("scanning","b5 uuid not matches..")
            }
        }else{
            zedLogNew("scanning","b5 $searchDevice not matches..")
        }
    }




    /*override fun onResume() {
        super.onResume()
    }*/

    override fun onDestroy() {
        super.onDestroy()


        if(confirmDialog != null){
            confirmDialog!!.dismiss()
        }



        if (dialog != null) {
            dialog!!.dismiss()
        }



        try{
            if (timer != null) {
                timer!!.cancel()
            }
        }catch (e: Exception){
            zedLogNew("timer",""+e.message)
        }


     //   rippleBackground!!.stopRippleAnimation()  // stop

        scanDevice(false) // onDestroy : stop scan
        //.......................................disconnect and close the connection
        if (gatt != null) {
            try{
                disconnectNew(gatt!!, "gatt is disconnected : on destroy. ")
            }catch (e :Exception){
                zedLogNew("vina1","0011a5"+e.message)
            }
        }
        zedLogNew("vina1", "on destroy : stop scan & disconnect gatt")






        //gps : stop location
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }catch (e : Exception){
            zedLogNew("vina1","stop location : " +e.message)
        }



        Log.d("tag02", "--->$sb")


        /* try{
             febyLogNew()
         }catch (e:Exception){
         }*/





    }

    companion object {
        var UART_UUID   = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")!!// UUIDs for UAT service and associated characteristics.
        var TX_UUID     = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")!!
        var RX_UUID     = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")!!
        var CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")!!// UUID for the BTLE client characteristic which is necessary for notifications.
    }




    //..............................................................................................send false message
    fun sendSingleMessage(message :String) {
        if (tx == null || message.isEmpty()) {
            zedLogNew("shuttle03","send message $message: failed do nothing : there is no device or message to send.")
            return
        }
        tx!!.value = message.toByteArray(Charset.forName("UTF-8"))
        if (ActivityCompat.checkSelfPermission(
                contextNew,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            return
        }
        if (gatt!!.writeCharacteristic(tx)) {
            statusSendMessage = false
            zedLogNew("shuttle03","-------------------->Sent: $message")


            handler.postDelayed(Runnable {
                try{
                    disconnectNew(gatt!!, "b8-gatt is disconnected : on illegal. ")

                    dialogMessageAlert ="Your stop request has been delivered to the driver."
                    val i = Intent()
                    i.putExtra("page_submit_message", "Your stop request has been delivered to the driver.")
                    setResult(444, i)
                    finish()

                }catch (e: Exception){
                }
            }.also { runnable = it }, 4000)



        } else {
            zedLogNew("shuttle03","Couldn't write TX characteristic!")
            dialogMessageAlert ="Your stop request has been delivered to the driver."
            //dialogMessageAlert ="Request timed out. Please try again after some time."
            val i = Intent()
            i.putExtra("page_submit_message", "Request timed out. Please try again after some time.")
            setResult(444, i)
            finish()

        }
    }
    private fun disconnectNew(gatt: BluetoothGatt, message: String) {

        mBluetoothDeviceAddress = null

        // disconnect fun will not support some devices, handler applied
        try{
            //Thread.sleep(2000)
            if (ActivityCompat.checkSelfPermission(
                    contextNew,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                return
            }
            gatt.disconnect()
            zedLogNew("vina1","-------------------->$message : disconnect success1")
        }catch (e :Exception){
            zedLogNew("vina1","-------------------->$message : disconnect : catch :"+e.message)
        }

        // close support all device , handler applied
        try{
            // Thread.sleep(2000)
            gatt.close()
            zedLogNew("vina1","-------------------->$message : disconnect success2")
        }catch (e :Exception){
            zedLogNew("vina1","-------------------->$message : close : catch :"+e.message)
        }
    }


    //..................................................................................................worst case scenario to disconnect higher version
    private fun scanDevice2() {
        val adapterBlu = contextNew.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if(adapterBlu!=null){
            if (!adapterBlu.adapter.isEnabled) {
                adapterBlu.adapter.isEnabled
            }
            else{
                try{
                    if(mLEScanner!=null){
                        if (ActivityCompat.checkSelfPermission(
                                contextNew,
                                Manifest.permission.BLUETOOTH_SCAN
                            ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ) {
                            return
                        }
                        mLEScanner!!.stopScan(highScanCallback2)
                    }
                }catch (e :Exception){}
            }
        }
        else{
        }
    }

    private val highScanCallback2 =
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                scanDevice2() // scan2 false

                try{
                    if (ActivityCompat.checkSelfPermission(
                            contextNew,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ) {
                        return
                    }
                    if (result.device.name != null && result.device.address != null){
                        val btDevice = result.device
                        val scanRecord    = result.scanRecord!!.bytes

                        if (searchDevice == result.device.name.toString()||searchDeviceC == result.device.name.toString() ||searchDeviceA == result.device.name.toString()) {
                            readyToConnectDevice2(btDevice,scanRecord)
                        }
                    }
                }catch (e :Exception){
                }
            }
        }

    fun readyToConnectDevice2(bluetoothDevice: BluetoothDevice, bytes: ByteArray) {
        if (ActivityCompat.checkSelfPermission(
                contextNew,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            return
        }
        if (searchDevice == bluetoothDevice.name.toString()||searchDeviceC == bluetoothDevice.name.toString() ||searchDeviceA == bluetoothDevice.name.toString()) {
            if (util.parseUUIDs(bytes).contains(UART_UUID)) {

                try{
                    gatt = bluetoothDevice.connectGatt(contextNew, false, callback2)
                }catch (e : Exception){
                }
            }
        }
    }
    private val callback2 = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (newState == BluetoothGatt.STATE_CONNECTED) {

                zedLogNew("vina1", "callback2 : $searchDevice:  is connected...")
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                zedLogNew("vina1", "callback2 : $searchDevice :  is dis-connected...")
            }
        }
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)

            zedLogNew("vina1", "callback2 : $searchDevice: is connected...done")
            try{
                if (ActivityCompat.checkSelfPermission(
                        contextNew,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ) {
                    return
                }
                gatt.disconnect()
            }catch (e :Exception){
            }
            try{
                gatt.close()
            }catch (e :Exception){
            }

        }
    }

    fun zedLogNew(tag : String, message: String){
        Log.d("tag01", "$tag $message")
    }






    //....................................................................gps : get lat & long from fused location.. (10m with 5 sec)
    @SuppressLint("MissingPermission")
    private fun locationFetch(){
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult ?: return
                try{
                    for (location in locationResult.locations){
                        try {
                            val currentLocation = locationResult.lastLocation
                            //if(currentLocation!=null){


                            gpsLatitude= currentLocation!!.latitude.toString()
                            gpsLongitute= currentLocation!!.longitude.toString()
                            zedLogNew("location", "location1$gpsLatitude,$gpsLongitute")
                            bindLocationUI()// bind address every 10 sec
                            //}
                        }catch (e:java.lang.Exception){
                        }
                    }
                }catch (e:Exception){}
            }
        }
        val locationRequest = LocationRequest.create()
        locationRequest.priority           = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.interval           = 10000
        locationRequest.fastestInterval    = 5000
        locationRequest.smallestDisplacement    = 20f
        //  locationRequest.numUpdates    = 1

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@AlertSOS)
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper()!!)
    }
    //....................................................................//bind location to ui ( api & geo code) with boolean check
    private fun bindLocationUI() {
        if(gpsLatitude!="0.0"){
            try{
                val geocode = Geocoder(applicationContext, Locale.getDefault())
                val addresses = geocode.getFromLocation(gpsLatitude.toDouble(), gpsLongitute.toDouble(), 1)
                //val address    = addresses[0].getAddressLine(0)
                //val city       = addresses[0].locality
                //val state      = addresses[0].adminArea
                //val zip        = addresses[0].postalCode
                //val country    = addresses[0].countryName
                //Log.d("address1", "$address : $city :  $state :$zip :$country " )
                zedLogNew("tarc3", "location : "+ addresses!![0].getAddressLine(0))

                stopRequestAddress = addresses[0].getAddressLine(0)
                statusBindLocationUI=true

            }catch (e : Exception){
                // with api : to find address
            }
        }
    }



    override fun onStart() {
        super.onStart()
        pageStatusBooleanCheck = true
    }

    override fun onStop() {
        super.onStop()
        pageStatusBooleanCheck = false


        try{
            if(runnable!=null){
                handler.removeCallbacks(runnable!!)
            }
            else{
            }
        }
        catch (e:Exception){
        }

    }


    override fun onBackPressed() {
        super.onBackPressed()
        Log.d("stop01","onBackPressed")
        dialogMessageAlert ="Your stop request has been delivered to the driver."
        val i = Intent()
        i.putExtra("page_submit_message", "Your stop request has been delivered to the driver.")
        setResult(444, i)
        finish()
    }


    private var launchStopRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            //val data: Intent = result.data!!
            mLEScanner = adapter!!.bluetoothLeScanner
            settings = ScanSettings.Builder()
                .build()
            scanDeviceSOS(contextNew) // onActivityResult
        }
    }

    private var launchStopRequestHigher =   registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        var permissionCheck = false
        permissions.entries.forEach {
            Log.d("test006", "${it.key} = ${it.value}")
            permissionCheck =it.value
        }
        if (permissionCheck) {
            //val data: Intent = result.data!!
            mLEScanner = adapter!!.bluetoothLeScanner
            settings = ScanSettings.Builder()
                .build()
            scanDeviceSOS(contextNew) // onActivityResult
        }
    }
}

