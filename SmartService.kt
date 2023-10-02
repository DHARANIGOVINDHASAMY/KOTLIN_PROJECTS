package mobility.lima.com.bibo
import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.BATTERY_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
import android.location.Location
import android.location.LocationManager
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.ViewModelProvider
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.RetryPolicy
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.google.android.gms.location.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import mobility.lima.com.BuildConfig
import mobility.lima.com.R
import mobility.lima.com.Seat.*
import mobility.lima.com.ZedUtil
import mobility.lima.com.api.ZigAPI
import mobility.lima.com.api.getRootUrl
import mobility.lima.com.api.getRootUrlV3
import mobility.lima.com.locationSetInterval
import mobility.lima.com.locationSmallestDisplacement
import mobility.lima.com.ui.*
import mobility.lima.com.ui.home.*
import mobility.lima.com.ui.schedule.convertTextToSpeech
import mobility.lima.com.utils.*
import org.chromium.net.CronetEngine
import org.chromium.net.UploadDataProviders
import org.chromium.net.UrlRequest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

internal var booleanCheckValidateIllegalOnce = true // default
internal var booleanCheckValidateOnce = true // default  // check illegal/ auto validate // activate
internal var booleanCheckValidateGattWrite  = true // auto validate
internal var disconnectRetry = 0
internal var globalStartTime= 0L



internal var needWakeCount = 1
internal var scanIntervalNewCount = 0
internal var screenWakeTimeInterval = 1

internal var majorMinor = "0"
internal var rssiUI = 0
internal var majorUI = 0
internal var minorUI = 0
internal var ibeaconMacAddress = ""
internal var ibeaconName = "BIBO 1.1 A"
//...............................................................................................
internal var globalRealtimeMacAddress="98:CD:AC:51:4D:AC"
internal var globalQuantityres=false
internal var globalClientType=2
internal var globalMaxTicket=6
internal var globalLogo=""
internal var globalClientName=""
internal var globalDescription=""
internal var globalBanner=""
internal var modeForIcon=""
//internal var beverageTicketStatus=false // default
internal var previousValidationNFC=false // default
internal var readyBooleanCheck=false //DEFAULT
internal var globalStartRssi=""
internal var apiLog = StringBuilder() // log for testing
internal var sbLog = StringBuilder() // log for testing
internal var biboInOutAutoValidate=false //default
internal var oregonShuttleOngoingTripStatus=true
internal var oregonInRangeStatus=false
internal var tarcInRangeStatus=false
internal var oregonDialogOnce=true
internal var oregonAutoStartService=false
internal var oregonShuttleOccupancies=-1
internal var oregonShuttleOccupanciesLatLngPoint = GeoPoint(0.0, 0.0)
internal var oregonLoginValidation = false // (transit schedule or nearby line = true)
internal var inOutData="IN"
internal var oregonBeaconStatus=true // it should be false - need to check
internal var globalDeviceAddress=""
internal var globalDeviceAddressOnew=""
internal var globalDeviceAddressOnewCount=1
internal var biboDataLimaTest=0
internal var globalDeviceAddressBnew="" // beverage data
internal var haveTicketId     = 0
internal var tempBLEAddress:BluetoothDevice?=null
internal var tempBLEByteArray: ByteArray? = null
internal var tempBleData: String? = ""
internal var x4maasServiceEnable = false
private var handler: Handler = Handler(Looper.myLooper()!!)
var runnable: Runnable? = null

internal var nearCount = 0


//...............................................................................................


// m1
class RssiFilter(windowSize: Int) {
    private val rssiWindow = mutableListOf<Int>()
    private val windowSize = windowSize

    fun addRssi(rssi: Int) {
        if (rssi >= 0) return // Ignore non-negative or invalid RSSI values (e.g., 127)

        rssiWindow.add(rssi)
        while (rssiWindow.size > windowSize) {
            rssiWindow.removeAt(0)
        }
    }

    fun getFilteredRssi(): Int {
        if (rssiWindow.size < windowSize) {
            return -300 // Not enough data for a valid average
        }

        val sortedRssi = rssiWindow.sorted()
        val filteredRssi = sortedRssi.subList(1, sortedRssi.size - 1)

        val sum = filteredRssi.sum()
        return sum / filteredRssi.size
    }
}

//https://github.com/AltBeacon/android-beacon-library/issues/186

/*

//m2
class KalmanFilter {
    private var estimate: Double = 0.0
    private var estimateError: Double = 1.0
    private var processNoise: Double = 0.01
    private var measurementNoise: Double = 1.0

    fun update(measurement: Double): Double {
        // Prediction
        val prediction = estimate
        val predictionError = estimateError + processNoise

        // Update
        val kalmanGain = predictionError / (predictionError + measurementNoise)
        estimate = prediction + kalmanGain * (measurement - prediction)
        estimateError = (1 - kalmanGain) * predictionError

        return estimate
    }
}
class RssiFilter(windowSize: Int) {
    private val rssiWindow = mutableListOf<Double>()
    private val windowSize = windowSize
    private val kalmanFilter = KalmanFilter() // Initialize Kalman filter

    fun addRssi(rssi: Int) {
        if (rssi >= 0) return // Ignore non-negative or invalid RSSI values (e.g., 127)

        val filteredRssi = kalmanFilter.update(rssi.toDouble())
        rssiWindow.add(filteredRssi)
        while (rssiWindow.size > windowSize) {
            rssiWindow.removeAt(0)
        }
    }

    fun getFilteredRssi(): Double {
        if (rssiWindow.size < windowSize) {
            return -300.0 // Not enough data for a valid average
        }

        val sum = rssiWindow.sum()
        return sum / rssiWindow.size
    }
}
*/



//m3
/*
class RssiFilter(windowSize: Int) {
    private val rssiWindow = mutableListOf<Double>()
    private val windowSize = windowSize
    private val kalmanFilter = KalmanFilter() // Initialize Kalman filter
    private val alpha = 0.2 // EMA smoothing factor
    private val outlierThreshold = 10 // Adjust as needed

    fun addRssi(rssi: Int) {
        if (rssi >= 0) return // Ignore non-negative or invalid RSSI values (e.g., 127)

        // Apply Kalman filter
        val kalmanFilteredRssi = kalmanFilter.update(rssi.toDouble())

        // Apply EMA
        val ema = if (rssiWindow.isEmpty()) kalmanFilteredRssi else
            alpha * kalmanFilteredRssi + (1 - alpha) * rssiWindow.last()

        // Add EMA-filtered RSSI to the window
        rssiWindow.add(ema)

        // Outlier detection and removal
        while (rssiWindow.size > windowSize) {
            val sortedRssi = rssiWindow.sorted()
            val median = sortedRssi[sortedRssi.size / 2]
            if (abs(ema - median) > outlierThreshold) {
                rssiWindow.removeAt(0) // Remove outlier
            } else {
                break // Stop removing outliers if within threshold
            }
        }
    }

    fun getFilteredRssi(): Double {
        if (rssiWindow.size < windowSize) {
            return -300.0 // Not enough data for a valid average
        }

        val sum = rssiWindow.sum()
        return sum / rssiWindow.size
    }
}

*/




internal var statusPreventDuplicate = true


class SmartService : Service() {
    private lateinit var model: TicketViewModel
    var count = 0
    var countFound = 0
    lateinit var btDevice: BluetoothDevice
    private var adapterLima: BluetoothAdapter? = null





    val eddystoneServiceId: ParcelUuid = ParcelUuid.fromString("88878A0C-34AE-4400-830C-84153FEC0F9A")

    val rssiFilter = RssiFilter(windowSize = configAverageRSSIValue) // dynamic rssi // configAverageValue // sample value
    // val rssiFilter = RssiFilter(windowSize = configAverageValue)

    var tempBooleanLog1 = true
    companion object {
        // UUIDs for UAT service and associated characteristics.
        var UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")!!
        var TX_UUID     = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")!!
        var RX_UUID     = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")!!
        // UUID for the BTLE client characteristic which is necessary for notifications.
        var CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")!!
    }
    var beaconRangeWithMyLocation = 0f
    var beaconRangeWithMyLocationStatus = true

    private var apiJob: Job? = null


    var booleanCheckAvoideFailureDuplicate = true


    var haveTickets  = false
    var haveValidTicket  = false
    var haveActiveTicket = false
    var haveNewTicket    = false
    var haveTicketIdDefault     = 0
    var activeTickets      : Int= -1
    var validatedTickets   : Int= -1
    var newTickets         : Int= -1
    var activeTicketsNew      : Int= -1



    private var currentDateAndTime: String = ""
    private var serviceBoo = false // destroy notification
    private lateinit var applicationContextNew: Context
    private val ioScope = CoroutineScope(Dispatchers.IO + Job() )
    var stopServiceCheck = 1// default
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var locationCallbackNotify: LocationCallback
    private lateinit var fusedLocationClientNotify: FusedLocationProviderClient


    var gpsLatitude   : String ="0.0"
    var gpsLongitute  : String= "0.0"
    var lastKnownMode = "Vehicle"
    var startTime=""
    private var oregonShuttleTripCompleted=true
    private var oregonShuttleOnGoingTrip=true
    internal var token       : String = ""
    private var mBluetoothDeviceAddress = ""//null
    var seatDatabase: SeatDatabase? = null
    internal var util = Util()
    var statusAfterConnect  = true
    private var searchDevice   : String= "BIBO 1.1 A"
    private var tx: BluetoothGattCharacteristic?  = null
    private var rx: BluetoothGattCharacteristic?  = null
    private var gatt: BluetoothGatt?             = null
    private var statusBeforeConnect = true
    private var booleanCheck   : Boolean= false // default
    private var booleanCheckShowNotification   : Boolean= false // default
    var needToStopServiceCheck = 1
    // for Scanning Ble Device in api version above 21
    private var settings: ScanSettings?         = null
    private val scanFilters = ArrayList<ScanFilter>()
    private var mLEScanner: BluetoothLeScanner? = null
    private var adapter: BluetoothAdapter?        = null
    private lateinit var mHandler: Handler
    private  var mRunnable: Runnable? = null

    private lateinit var mHandlerBeverage: Handler
    private  var mRunnableBeverage: Runnable? = null


    //..........................................................
    var statusService = true //default
    //..........................................................


    private var statusBIBONotFoundCount=0 // default
    var utilSharedPref : SharedPreference?=null
    var test01 = 0f
    var test02 = 0

    //remove special characters and numbers
    val removeSpecial = Regex("[^A-Za-z ]")

    //newFormat values
    var ticketsType = "0"
    var sendTicketId = ""




    //**********************************************************************************************
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }






    private var liveTickets : ArrayList<Ticket>?=null
    override fun onCreate() {
        super.onCreate()
        smartLog("zed007","onCreate init : ${getTime()}")

        adapterLima = BluetoothAdapter.getDefaultAdapter() // classic scan

        //..........................live ticket status
        liveTickets =ArrayList()
        model = ViewModelProvider.AndroidViewModelFactory.getInstance(application).create(TicketViewModel::class.java)
        model.allTicket.observeForever { ticket ->
            if (ticket != null) {
                // Process the ticket data here
                liveTickets?.clear()
                liveTickets?.addAll(ticket)
                myLog("mangologz", "ticket size ${ticket.size}")

                haveValidTicket  = false
                haveActiveTicket = false
                haveNewTicket = false
                haveTicketId = 0
                haveTicketIdDefault = 0
                validatedTickets = 0
                activeTickets = 0
                newTickets = 0
                activeTicketsNew = 0

                if (ticket.isNotEmpty()) {
                    haveTickets = true
                    for (i in ticket.indices) {

                        if (1 == ticket[i].status || 2 == ticket[i].status) {
                            myLog("zed007","* live status : ${ticket[i].status}==> tid: ${ticket[i].ticketId}")
                        }
                        if (3 == ticket[i].status) {
                            haveValidTicket = true
                            validatedTickets++
                        }
                        if ((2 == ticket[i].status)) {
                            haveActiveTicket = true
                            activeTickets++
                        }
                        if (1 == ticket[i].status) {
                            haveNewTicket = true
                            newTickets++
                            haveTicketIdDefault = ticket[i].ticketId
                        }
                    }

                    if(haveValidTicket || haveActiveTicket){
                        haveNewTicket = false // bez user already validate or active tickets : no need auto validate
                    }
                    if ((activeTickets == 0 && validatedTickets == 0)){
                        needScan = true // M1 : NO
                    }
                    else if (((activeTickets > 0 && validatedTickets == 0) || (activeTickets > 0 && validatedTickets > 0))) {
                        needScan = true // M2 : ACTIVE
                    }
                    else if (haveNewTicket && (haveTicketIdDefault>0)) {
                        needScan = true // M3 : NEW
                    }
                    else if (activeTickets == 0 && validatedTickets > 0) {
                        // needScan = outDataBoolean // M4 : VALIDATED // OUT
                        needScan = true // need to check again // to stop service
                    }
                    else{
                        smartLog("mangologz", "M5 :?")
                    }
                }
                else{
                    haveTickets = false
                }
            }
        }

        //global
        applicationContextNew=applicationContext
        utilSharedPref = SharedPreference(applicationContextNew)
        token = utilSharedPref!!.getValueString("user_token").toString()

        // foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            startOreoForegroundNotification("Welcome aboard")
            serviceBoo = true // higher version
        }
        else{
            startNotification("Welcome aboard")
        }


        // find location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val lm = applicationContextNew.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (LocationManagerCompat.isLocationEnabled(lm)) {
                //**********************************location01
                locationCallback = object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        for (location in locationResult.locations){
                            val currentLocation   = locationResult.lastLocation
                            gpsLatitude    = currentLocation!!.latitude.toString()
                            gpsLongitute   = currentLocation.longitude.toString()

                            stopServiceCheck++
                            // pending stop service

                        }
                    }
                }
            }
            else{
                myLog("zed007", "gps is off!")
            }

        }
        else{
            myLog("zed007", "location permission is off!")
        }

        goScheduleFeature()
    }


    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //.......................................room config
        seatDatabase = SeatDatabase.getDatabase(this)

        // location init
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            //**********************************location01
            val lm = applicationContextNew.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (LocationManagerCompat.isLocationEnabled(lm)) {
                val locationRequest = LocationRequest.create()

                locationRequest.priority           = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
                locationRequest.interval           = 60000
                locationRequest.fastestInterval    = 60000
                locationRequest.smallestDisplacement    = 20f

                fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@SmartService)
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper()!!)
            }
            else{
                myLog("limadata3", "gps is turn off")
            }

        }else{
            myLog("limadata3", "location permission is off")
        }


        goScheduleFeatureInit()

        // ble setting
        val adapterBlu = applicationContextNew.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = adapterBlu.adapter

        mLEScanner = adapter!!.bluetoothLeScanner
        settings   = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val scanFilter = ScanFilter.Builder()
            .build()
        scanFilters.add(scanFilter)


       /* val lmInit = applicationContextNew.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val adapterBluHandlerInit = applicationContextNew.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED && LocationManagerCompat.isLocationEnabled(lmInit) && adapterBluHandlerInit.adapter.isEnabled) {
        }*/


        //....................................... initialize the handler instance
        mHandler = Handler(Looper.getMainLooper())

        if (mRunnable != null) {
            mHandler.removeCallbacks(mRunnable!!)
        }
        mRunnable = Runnable {


            if (utilSharedPref!!.getValueString("user_token")!="") {
                needWakeCount = 0 // handler reset
                utilSharedPref!!.save("s_wallet_api", true)
                utilSharedPref!!.save("s_activate_api", true)

                scanIntervalNewCount++;
                smartLog("mangologz", "scan $scanIntervalNewCount ")


                smartLog("ble009", "............................................handler $scanIntervalNew")

                readyBooleanCheck =true // HANDLER
                statusBeforeConnect=true // 1 mt

                myLog("ble009","handler init.. $globalDeviceAddress  $statusService ")

                // checking: location permission, gps, bluetooth permission & bibo is enable?
                val adapterBluHandler = applicationContextNew.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val lm = applicationContextNew.getSystemService(Context.LOCATION_SERVICE) as LocationManager

                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    LocationManagerCompat.isLocationEnabled(lm) &&
                    adapterBluHandler.adapter.isEnabled
                    && (globalDeviceAddress!="" || globalDeviceAddressBnew!="")
                ) {

                    //&& statusService
                    statusBIBONotFoundCount=0 // granted



                    stopServiceCheck =1 // bibo match
                    booleanCheckAvoideFailureDuplicate = true//RESET

                    if(oregonShuttleOnGoingTrip){
                        oregonShuttleOnGoingTrip = false
                        oregonShuttleTripCompleted=true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                            startOreoForegroundNotification("Welcome aboard")
                            serviceBoo = true // higher version
                        }
                        else{
                            startNotification("Welcome aboard")
                        }
                    }

                    // api call every 1 min
                    myLog("ble0092","api call every 1 min init.. $globalClientType")


                    if(globalClientType==3){// || globalClientType==4
                        if(gpsLatitude=="0.0" || gpsLongitute=="0.0"){
                            gpsLatitude  = phoneLatitude
                            gpsLongitute = phoneLongitute
                        }
                        myLog("limadata3", "002 type 3 clackmass")
                        biboData(gpsLatitude,gpsLongitute,applicationContextNew,globalDeviceAddressOnew,"IN") // IN  || globalClientType==3
                    }

                }
                else{
                    statusBIBONotFoundCount++ // deny
                    // end trip notification once

                    myLog("ble009","go: $oregonShuttleTripCompleted : $globalDeviceAddress")


                    if (oregonShuttleTripCompleted && globalDeviceAddress!="" && statusBIBONotFoundCount >=3){
                        oregonShuttleTripCompleted=false
                        oregonShuttleOnGoingTrip = true

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            serviceBoo = true // higher version
                            startOreoForegroundNotification("Thank you for riding with us")
                        } else {
                            startNotification("Thank you for riding with us")
                        }

                        myLog("limadata","new location init : $globalClientType : $globalDeviceAddressOnewCount ")


                        // out api call
                        myLog("ble009","api call end trip init")
                        if(globalClientType==3 ){ //|| globalClientType==4
                            if(gpsLatitude=="0.0" || gpsLongitute=="0.0"){
                                gpsLatitude  = phoneLatitude
                                gpsLongitute = phoneLongitute
                            }

                            if(outDataBoolean){
                                myLog("limadata3", "003 clackmass")
                                biboData(
                                    gpsLatitude,
                                    gpsLongitute,
                                    applicationContextNew,
                                    globalDeviceAddressOnew,
                                    "OUT"
                                )

                            }
                        }

                        myLog("ble009","api call end trip")
                    }


                    // stop service :

                    if(statusBIBONotFoundCount >= scanServiceIntervalRetry){
                        statusBIBONotFoundCount=0 // before stop service
                        myLog("ble009","stop if statusBIBONotFoundCount :  $statusBIBONotFoundCount")

                        try{
                            stopSmartService(applicationContextNew) //success
                        }catch (e:java.lang.Exception){
                        }

                    }
                    else if(statusBIBONotFoundCount >=15){
                        globalDeviceAddress=""
                        globalDeviceAddressOnew="" //  >=15
                        globalDeviceAddressBnew=""
                    }else{
                        myLog("ble009","stop else statusBIBONotFoundCount :  $statusBIBONotFoundCount")
                    }
                }

                scanLeDevice(true, "handler") // * start scan HANDLER when service start
                screenWakeTimeInterval++

                mHandler.postDelayed(mRunnable!!, scanIntervalNew.toLong()) //1 min delay : will repeat
            }
            else{
                myLog("ble009", "service stop 02")
                stopSmartService(applicationContextNew)
            }
        }
        mHandler.postDelayed(mRunnable!!, 500) // 0.5 sec delay


        //beverage handler
        mHandlerBeverage = Handler(Looper.getMainLooper())
        if (mRunnableBeverage != null) {
            mHandlerBeverage.removeCallbacks(mRunnableBeverage!!)
        }
        mRunnableBeverage = Runnable {
            if (globalDeviceAddressBnew!=""){
                if(outDataBoolean){
                    sendBeverageData() // HANDLER
                }
                else{
                }
            }
            else{
            }
            mHandlerBeverage.postDelayed(mRunnableBeverage!!, 30000) //10 sec delay
        }
        mHandlerBeverage.postDelayed(mRunnableBeverage!!, 500) // 0.5 sec delay



        return START_STICKY
    }


    //.............................................................validation
    //.............................................................others
    private fun goScheduleFeature() {
        // pending testing & use correct log
       try{
           if (isGoEnabled){
               myLog("checkSound","triggered")
               if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                   val lm = applicationContextNew.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                   if (LocationManagerCompat.isLocationEnabled(lm)) {
                       //**********************************location01
                       locationCallbackNotify = object : LocationCallback() {
                           override fun onLocationResult(locationResult: LocationResult) {
                               for (location in locationResult.locations){
                                   val currentLocation   = locationResult.lastLocation
                                   gpsLatitude    = currentLocation!!.latitude.toString()
                                   gpsLongitute   = currentLocation.longitude.toString()

                                   val speed3 = location.speed
                                   val currentSpeed = round(speed3.toDouble())
                                   val kphSpeed = round((currentSpeed*3.6))
                                   val mph=(kphSpeed/1.6093).roundToInt()

                                   modeForIcon =  if ((mph <= 0))
                                   {
                                       "Walking"
                                   }
                                   else if (mph > 20) {
                                       "Bus"
                                   }
                                   else{
                                       "Walking"
                                   }

                                   if(intermediateStopListNewTemp.isNotEmpty()) {
                                       try{
                                           var booleanCheckOnce = true
                                           //intermediateStopListNewTemp.reverse()
                                           for (i in 0 until intermediateStopListNewTemp.size) {

                                               try{
                                                   val distanceValue=distFrom(currentLocation.latitude.toFloat(), currentLocation.longitude.toFloat(), intermediateStopListNewTemp[i].StopLat.toFloat(),  intermediateStopListNewTemp[i].StopLng.toFloat())
                                                   if(distanceValue<70 && booleanCheckOnce){
                                                       booleanCheckOnce = false
                                                       stopServiceCheck =1// intermediate stop

                                                       if(intermediateStopListNewTemp.size!=1) {
                                                           ZedUtil().showNotification(applicationContextNew, "Approaching Stop: ${intermediateStopListNewTemp[i].stopName}")
                                                           convertTextToSpeech(applicationContextNew,"Approaching Stop: ${intermediateStopListNewTemp[i].stopName}")

                                                       }else{
                                                           ZedUtil().showNotification(applicationContextNew, "Arriving destination Stop: ${intermediateStopListNewTemp[i].stopName}")
                                                           convertTextToSpeech(applicationContextNew,"Arriving destination Stop: ${intermediateStopListNewTemp[i].stopName}")
                                                       }


                                                       //...................................
                                                       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                                                           startOreoForegroundNotification("Welcome aboard")
                                                           serviceBoo = true // higher version
                                                       }
                                                       else{
                                                           startNotification("Welcome aboard")
                                                       }

                                                       myLog("bus_stop_remind_03","stop name :"+intermediateStopListNewTemp[i].stopName+" & Distance : $distanceValue")
                                                       //...................................
                                                       myLog("bus_stop_remind_03","before:"+intermediateStopListNewTemp.size.toString())
                                                       intermediateStopListNewTemp.removeAt(i)
                                                       myLog("bus_stop_remind_03", "after:${intermediateStopListNewTemp.size}")
                                                       break
                                                   }else{
                                                       myLog("bus_stop_remind_03","Distance : $distanceValue :: ${intermediateStopListNewTemp[i].stopName}")
                                                   }
                                               }
                                               catch (e:java.lang.Exception){
                                                   myLog("bus_stop_remind_03", "e002 :${e.message}")
                                               }
                                           }
                                       }
                                       catch (e:java.lang.Exception){
                                           myLog("bus_stop_remind_03", "e001 :${e.message}")
                                       }
                                   }
                               }
                           }
                       }
                   }
                   else{
                       myLog("location01", "notification gps is turn off")
                   }

               }
               else{
                   myLog("location01", "notification location permission is off")
               }
           }
           else{
               try{
                   if (fusedLocationClientNotify != null) {
                       fusedLocationClientNotify.removeLocationUpdates(locationCallbackNotify)
                   }
               }catch (e: Exception){

               }
           }
       }catch (e:Exception){}
    }
    private fun goScheduleFeatureInit(){
        if (isGoEnabled){
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                //**********************************location01
                val lm = applicationContextNew.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                if (LocationManagerCompat.isLocationEnabled(lm)) {
                    val locationRequest = LocationRequest.create()
                    locationRequest.priority           = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
                    locationRequest.interval           = locationSetInterval
                    locationRequest.fastestInterval    = locationSetInterval/2
                    locationRequest.smallestDisplacement    = locationSmallestDisplacement
                    fusedLocationClientNotify = LocationServices.getFusedLocationProviderClient(this@SmartService)
                    fusedLocationClientNotify.requestLocationUpdates(locationRequest, locationCallbackNotify, Looper.myLooper()!!)
                }
                else{
                    myLog("location01", "notification gps is turn off")
                }

            }else{
                myLog("location01", "notification location permission is off")
            }
        }
        else{
            try{
                if (fusedLocationClientNotify != null) {
                    fusedLocationClientNotify.removeLocationUpdates(locationCallbackNotify)
                }
            }catch (e: Exception){
            }
        }
    }

    //old namenewformat
    fun nameNewFormat(userNameValue:String,context: Context):String{
        utilSharedPref = SharedPreference(context)
        var userName = userNameValue
        myLog("test0011", "" + userNameValue)
        if (utilSharedPref!!.getValueString("user_first_name").toString().isNullOrEmpty()) {
            userName= utilSharedPref!!.getValueString("user_name").toString()
        }
        myLog("test0011", "" + userName)
        val removeSpecial = Regex("[^A-Za-z ]")
        var userNameValueFormated = removeSpecial.replace(userName, "")
        return if (userNameValueFormated.length>9) {
            userNameValueFormated.slice(0..9)
        }else{
            userNameValueFormated
        }


    }
    fun nameNewFormat(userNameValue:String):String{
        var userName = userNameValue
        myLog("test0011", "" + userNameValue)
        if (utilSharedPref!!.getValueString("user_first_name").toString().isNullOrEmpty()) {
            userName= utilSharedPref!!.getValueString("user_name").toString()
        }
        myLog("test0011", "" + userName)
        val removeSpecial = Regex("[^A-Za-z ]")
        var userNameValueFormated = removeSpecial.replace(userName, "")
        return if (userNameValueFormated.length>9) {
            userNameValueFormated.slice(0..9)
        }else{
            userNameValueFormated
        }


    }



    override fun onDestroy() {
        super.onDestroy()
        intermediateStopListNewBoolean = false
        smartLog("ble009","onDestroy done")

        if(mLEScanner!=null){
            try{
                if (ActivityCompat.checkSelfPermission(
                        applicationContextNew,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ) {

                    return
                }
                mLEScanner!!.stopScan(highScanCallbackClackmass)
            }catch (e: Exception){
                smartLog("ble009", "onDestroy stop high scanning catch : " + e.message.toString())
            }
            try{
                myLog("checkHitWorks","highScanCallback -> stop scan 1")

                mLEScanner!!.stopScan(highScanCallback)
            }catch (e: Exception){
                smartLog("ble009", "onDestroy stop high scanning catch : " + e.message.toString())
            }
        }

        try{
            if (Build.VERSION.SDK_INT >= 26 && serviceBoo) {
                stopForeground(true)
            }
        }catch (e:java.lang.Exception){
        }

        try{
            if (fusedLocationClient != null) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        }catch (e: Exception){

        }

        try{
            if (fusedLocationClientNotify != null) {
                fusedLocationClientNotify.removeLocationUpdates(locationCallbackNotify)
            }
        }catch (e: Exception){

        }

        if(mRunnable!=null){
            mHandler.removeCallbacks(mRunnable!!)
        }

        //BEVERAGE
        if(mRunnableBeverage!=null){
            mHandlerBeverage.removeCallbacks(mRunnableBeverage!!)
        }

        globalDeviceAddress =""
        globalDeviceAddressOnew="" // on destroy
        globalDeviceAddressBnew=""
    }
    //**********************************************************************************************




    private val classicScanCallbackLima = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    myLog("limadata3", "ACTION_FOUND")

                    try {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if (device != null && device.name != null) {
                            myLog("limadata3", "$countFound / $count==> ${device.name}")

                            if (device.name.contains("ZIG_")) {
                                val parts = device.name.split("_")
                                val limaValue = parts.getOrNull(0)
                                countFound++
                                myLog("limadata3", " * scan found : ${device.name} : $limaValue")
                                myLog("ble0096", "lime remove *1* "+limaValue.toString())

                                try{
                                    if ("ZIG" == limaValue) {
                                        globalDeviceAddressOnewCount = 1
                                        myLog("ble0096", "login status true")
                                        scanLeDevice(false,"clackmass")
                                        myLog("limadata", "BIBO O clackmass:: " +device.address.toString() + device.name.toString())
                                        statusService = true // BIBO 1.1 O clakamas


                                        val prefix = "ZIG_"
                                        val suffix = "_LE"
                                        var macAddress = device.name.removePrefix(prefix).removeSuffix(suffix)

                                        globalDeviceAddress = device.address.toString() //clakamas 0
                                        //globalDeviceAddressOnew = device.address.toString() //CLACKMASS

                                        // to retrieve the MAC address from the "scan device name" instead of the "scan MAC address"
                                        globalDeviceAddressOnew = macAddress.chunked(2).joinToString(":")

                                        biboDataLima(gpsLatitude, gpsLongitute, applicationContextNew, globalDeviceAddressOnew, "OUT") //OUT
                                    }
                                    else{
                                        myLog("ble0096", "lime remove1: "+ device.name.toString())
                                    }
                                }catch (e:Exception){
                                    myLog("limadata3", " exception catch ${e.message}")
                                }
                            }
                            else{
                                myLog("limadata3", " * scanning..")
                            }
                        }
                    }
                    catch (e: Exception) {
                        // Handle the exception or log an error message
                        myLog("limadata3", "BluetoothDevice.ACTION_FOUND: ${e.message}")
                    }


                }

            }
        }

        private fun containsUUID(uuids: Array<ParcelUuid>, targetUUID: UUID): Boolean {
            for (uuid in uuids) {
                if (uuid.uuid == targetUUID) {
                    return true
                }
            }
            return false
        }
    }



    var scanStartTime: Long =0
    var time1: Long =0
    var needScan = true // default







    //..............................................................................................ibeacon start
    // start or stop scan
    @SuppressLint("MissingPermission")
    private fun scanLeDevice(enable: Boolean, message: String) {

        if (ActivityCompat.checkSelfPermission(applicationContextNew, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return
        }
        if(globalClientType==4){

            // * lima client
            if (enable) {
                // ................................................................
                // apply the cpu wakeup code instead of screen wake
                if(screenWakeTimeInterval>=10){
                    screenWakeTimeInterval = 0

                    /*try {
                        val pm: PowerManager = getSystemService(POWER_SERVICE) as PowerManager
                        val isScreenOn: Boolean = pm.isInteractive // check if screen is on
                        if (!isScreenOn) {
                            val wl: PowerManager.WakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "myApp:notificationLock")
                            wl.acquire(500) //set your time in milliseconds
                        }
                    }catch (e:java.lang.Exception){
                        smartLog("limadata3", "wake catch 2:${e.message}")
                    }*/
                }
                // review the code:................................................................


                try{
                    /* val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                     registerReceiver(classicScanCallbackLima, filter)
                     adapterLima?.startDiscovery()*/
                    val filter = IntentFilter()
                    filter.addAction(BluetoothDevice.ACTION_FOUND)
                    //filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                    registerReceiver(classicScanCallbackLima, filter)
                    if (adapterLima?.isEnabled == true) {
                        adapterLima?.startDiscovery()
                    } else {
                        // Bluetooth is not enabled, request user to enable it
                        // You can use startActivityForResult to request the user to enable Bluetooth
                        myLog("limadata3", "Please enable Bluetooth to scan for devices")
                    }
                }catch (e:Exception){
                    myLog("limadata3", "catch lima start scan ${e.message}")
                }
            }
            else {
                try{
                    adapterLima?.cancelDiscovery()
                    unregisterReceiver(classicScanCallbackLima)
                }catch (e:Exception){
                    myLog("limadata3", "catch lima end scan ${e.message}")
                }
            }

        }
        else{
            smartLog("zed007", "scan init $globalClientType is ticket validation : ${getTime()}")

            // ** wake screen only when active tickets
            if(haveActiveTicket){
                try {
                    val pm: PowerManager = getSystemService(POWER_SERVICE) as PowerManager
                    val isScreenOn: Boolean = pm.isInteractive // check if screen is on
                    if (!isScreenOn) {
                        val wl: PowerManager.WakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "myApp:notificationLock")
                        wl.acquire(9000) //set your time in milliseconds
                    }
                }catch (e:java.lang.Exception){
                    smartLog("zed007", "wake catch 2:${e.message}")
                }
            }

            // ** init ble scanner
            mLEScanner = adapter!!.bluetoothLeScanner

            // **  scan faster only when active tickets
            settings = if((activeTickets == 0 && validatedTickets == 0) || haveActiveTicket || haveNewTicket){
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
            } else{
                ScanSettings.Builder()
                    .build()
            }
            // ** custom scan specified devices only with ScanFilter
            val scanFilter = ScanFilter.Builder()
                //.setServiceUuid(ParcelUuid_GENUINO101_ledService) // *** need to test all phone
                .build()
            scanFilters.add(scanFilter)


            // **  Ble mode
            if(utilSharedPref!!.getValueString("setting_mode")=="Bluetooth" || utilSharedPref!!.getValueString("setting_mode")==""){

                if (enable) {
                    globalStartTime = System.currentTimeMillis()
                    SmartService().smartLog("zed007", " ")
                    SmartService().smartLog("zed007", " ")
                    SmartService(). smartLog("zed007", "........................ ")
                    smartLog("zed007", "start scan int: ${getTime()}")

                    if(mLEScanner!=null){
                        statusService=false // scan start

                        //**********************************************************************
                        when (globalClientType) {
                            2 -> {
                                smartLog("zed007", "init  :: scan $message : validation prevent check :  $booleanCheckValidateOnce")

                                ioScope.launch {
                                    //..............................................xxxx
                                    //val ticket = seatDatabase!!.TicketDao().selectAllTicket()
                                    val ticket = liveTickets


                                    haveValidTicket  = false
                                    haveActiveTicket = false
                                    haveNewTicket = false

                                    haveTicketId = 0
                                    haveTicketIdDefault = 0
                                    validatedTickets = 0
                                    activeTickets = 0
                                    newTickets = 0
                                    activeTicketsNew = 0

                                    if (ticket?.isNotEmpty() == true) {
                                        haveTickets = true
                                        for (i in ticket.indices) {

                                            if (1 == ticket[i].status || 2 == ticket[i].status) {
                                                myLog("zed007","* status : ${ticket[i].status}==> tid: ${ticket[i].ticketId}")
                                            }


                                            if (3 == ticket[i].status) {
                                                haveValidTicket = true
                                                validatedTickets++
                                            }
                                            if ((2 == ticket[i].status)) {
                                                haveActiveTicket = true
                                                activeTickets++
                                            }
                                            if (1 == ticket[i].status) {
                                                haveNewTicket = true
                                                newTickets++
                                                haveTicketIdDefault = ticket[i].ticketId

                                            }
                                        }


                                        if(haveValidTicket || haveActiveTicket){
                                            haveNewTicket = false // bez user already validate or active tickets : no need auto validate
                                        }

                                        if ((activeTickets == 0 && validatedTickets == 0)){
                                            needScan = true // M1 : NO
                                        }
                                        else if (((activeTickets > 0 && validatedTickets == 0) || (activeTickets > 0 && validatedTickets > 0))) {
                                            needScan = true // M2 : ACTIVE
                                        }
                                        else if (haveNewTicket && (haveTicketIdDefault>0)) {
                                            needScan = true // M3 : NEW
                                        }
                                        else if (activeTickets == 0 && validatedTickets > 0) {
                                            // needScan = outDataBoolean // M4 : VALIDATED // OUT
                                            needScan = true // need to check again

                                            // if false means = new ticker user - have to wait 10 sec or
                                            // restart service + average rssi also 10 sec??
                                        }
                                        else{
                                            smartLog("zed007", "M5 :?")
                                        }

                                        // always scan
                                        try{
                                            smartLog("zed007","highScanCallback init")
                                            mLEScanner!!.startScan(scanFilters, settings, highScanCallback) // * active or no ticket
                                        }catch (e: Exception){
                                            smartLog("zed007", "highScanCallback catch : " + e.message.toString())
                                        }
                                        //.....................................................................
                                    }
                                    else{

                                        if(booleanCheckValidateIllegalOnce){
                                            haveTickets = false
                                            smartLog("zed007", "high scanning : no tickets")
                                            try{
                                                smartLog("zed007","highScanCallback init")
                                                mLEScanner!!.startScan(scanFilters, settings, highScanCallback) // * active or no ticket
                                            }catch (e: Exception){
                                                smartLog("zed007", "highScanCallback catch : " + e.message.toString())
                                            }
                                        }
                                        else{
                                            smartLog("zed007", "illegal already send")
                                        }

                                    }

                                }



                            }
                            3,10 -> {
                                searchDevice ="BIBO 1.1 O"
                                try{
                                    smartLog("zed007", "high scanning clackmass")
                                    mLEScanner!!.startScan(scanFilters, settings, highScanCallbackClackmass)
                                }catch (e: Exception){
                                    smartLog("zed007", "high scanning catch clackmass: " + e.message.toString())
                                }
                            }
                            else -> {
                                smartLog("zed007", "else $globalClientType : no action")
                            }
                        }
                        //**********************************************************************
                    }
                }
                else {
                    //........................................................................
                    smartLog("zed007", "stop scan int: ${getTime()}")
                    if(mLEScanner!=null){
                        try{
                            mLEScanner!!.stopScan(highScanCallbackClackmass)  // client type is 3
                        }catch (e: Exception){
                            smartLog("zed007", "stopScan highScanCallbackClackmass catch2: " + e.message.toString())
                        }
                        try{
                            mLEScanner!!.stopScan(highScanCallback)
                        }catch (e: Exception){
                            smartLog("zed007", "stopScan highScanCallback catch1 : " + e.message.toString())
                        }
                    }
                    //........................................................................
                }
            }
            else{

                // **  stop scan NFC / QR mode
                if(mLEScanner!=null){
                    try{
                        mLEScanner!!.stopScan(highScanCallbackClackmass)
                    }catch (e: Exception){
                        smartLog("zed007", "stop highScanCallbackClackmass catch4 : " + e.message.toString())
                    }
                    try{
                        mLEScanner!!.stopScan(highScanCallback)
                    }catch (e: Exception){
                        smartLog("zed007", "stop highScanCallback catch3 : " + e.message.toString())
                    }
                }
                try {
                    if (mRunnable != null) {
                        mHandler.removeCallbacks(mRunnable!!)
                    }
                }catch (e:Exception){
                    myLog("zed007","handler catch ${e.message}")
                }
            }
        }
    }

    // high Scan callback : find near far or immediate
    private val highScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try{

                if (ActivityCompat.checkSelfPermission(applicationContextNew, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    return
                }
                //.....................................................
                val scanRecord      = result.scanRecord
                val beacon          = Beacon(result.device.address)
                beacon.manufacturer = result.device.name
                beacon.rssi         = result.rssi

                if (scanRecord != null) {
                    val serviceUuids = scanRecord.serviceUuids
                    val iBeaconManufactureData = scanRecord.getManufacturerSpecificData(0X004c)
                    if (serviceUuids != null && serviceUuids.size > 0 && serviceUuids.contains(eddystoneServiceId)) {

                        val serviceData = scanRecord.getServiceData(eddystoneServiceId) // review
                        if (serviceData != null && serviceData.size > 18) {
                            val eddystoneUUID            = Utils.toHexString(Arrays.copyOfRange(serviceData, 2, 18))
                            val namespace                = String(eddystoneUUID.toCharArray().sliceArray(0..19))
                            val instance                 = String(eddystoneUUID.toCharArray().sliceArray(20 until eddystoneUUID.toCharArray().size))
                            beacon.type         = Beacon.beaconType.eddystoneUID
                            beacon.namespace    = namespace
                            beacon.instance     = instance
                        }
                    }
                    if (iBeaconManufactureData != null && iBeaconManufactureData.size >= 23) {
                        val iBeaconUUID = Utils.toHexString(iBeaconManufactureData.copyOfRange(2, 18))
                        val major = Integer.parseInt(Utils.toHexString(iBeaconManufactureData.copyOfRange(18, 20)), 16)
                        val minor = Integer.parseInt(Utils.toHexString(iBeaconManufactureData.copyOfRange(20, 22)), 16)
                        beacon.type     = Beacon.beaconType.iBeacon
                        beacon.uuid     = iBeaconUUID
                        beacon.major    = major
                        beacon.minor    = minor

                        if (beacon.major == 100){
                            rssiFilter.addRssi(beacon.rssi!!)
                            val filteredRssi         = rssiFilter.getFilteredRssi()
                            minorUI                  = beacon.minor!!
                            majorUI                  = beacon.major!!
                            majorMinor               = "${majorUI}${minorUI}"
                            ibeaconMacAddress        = beacon.macAddress?.toString()!!
                            globalDeviceAddressBnew  = ibeaconMacAddress
                            rssiUI = try {
                                filteredRssi
                            }catch (e:java.lang.Exception){
                                0
                            }
                            val distance = findProximity(filteredRssi,":: ${beacon.rssi} -> ${beacon.major} ${beacon.minor} ${ibeaconMacAddress}")
                            // smartLog("zed007", "$distance  :: ${beacon.rssi} -> ${beacon.major} ${beacon.minor} ${ibeaconMacAddress}")
                            statusBIBONotFoundCount = 0



                            if((activeTickets == 0 && validatedTickets == 0) || haveActiveTicket || haveNewTicket){
                                // near check : for immediate & far
                                if (configIbeaconAndroidStatus=="3" && (distance == "Immediate" || distance == "Near"  || distance == "Far") && booleanCheckValidateOnce) {
                                    booleanCheckValidateOnce    = false //3 is near

                                    globalStartRssi         = "" + result.rssi
                                    globalDeviceAddress     = ibeaconMacAddress // bibo a - ticket flow
                                    tempBLEAddress              = result.device

                                    readyBooleanCheck       = false // BOOLEAN FALSE ?
                                    statusService           = true //BIBO 1.1 A ?

                                    scanLeDevice(false, "") // ** stop scan
                                    findTicketStatus() // * validation near

                                    sendBeverageData() // IN BEVERAGE RANGE & // recheck0001
                                }
                                else if (configIbeaconAndroidStatus=="2" &&  (distance == "Immediate" || distance == "Near") && booleanCheckValidateOnce) {
                                    booleanCheckValidateOnce    = false //2 is near

                                    globalStartRssi         = "" + result.rssi
                                    globalDeviceAddress     = ibeaconMacAddress // bibo a - ticket flow
                                    tempBLEAddress              = result.device

                                    readyBooleanCheck       = false // BOOLEAN FALSE ?
                                    statusService           = true //BIBO 1.1 A ?

                                    scanLeDevice(false, "") // ** stop scan
                                    findTicketStatus() // * validation near

                                    sendBeverageData() // IN BEVERAGE RANGE & // recheck0001
                                }
                                else if (configIbeaconAndroidStatus=="1" &&  (distance == "Immediate") && booleanCheckValidateOnce) {
                                    booleanCheckValidateOnce    = false //1 is near

                                    globalStartRssi         = "" + result.rssi
                                    globalDeviceAddress     = ibeaconMacAddress // bibo a - ticket flow
                                    tempBLEAddress              = result.device

                                    readyBooleanCheck       = false // BOOLEAN FALSE ?
                                    statusService           = true //BIBO 1.1 A ?

                                    scanLeDevice(false, "") // ** stop scan
                                    findTicketStatus() // * validation near

                                    sendBeverageData() // IN BEVERAGE RANGE & // recheck0001
                                }
                                else{
                                    // smartLog("zed007","Duplicate prevent $booleanCheckValidateOnce $activeTickets $configIbeaconAndroidStatus")
                                }
                            }
                            else{
                                myLog("zed007","check ticket status :a0: $activeTickets v0:$validatedTickets  a:$haveActiveTicket n:$haveNewTicket")
                            }

                        }
                        else if (beacon.major == 102 && utilSharedPref!!.getValueBoolean("beverageStatus", false)) {

                            rssiFilter.addRssi(beacon.rssi!!)
                            val filteredRssi = rssiFilter.getFilteredRssi()
                            rssiUI = try {
                                filteredRssi
                            }catch (e:java.lang.Exception){
                                0
                            }

                            smartLog("ble009", "init bibo b *1")
                            myLog("ble00921", "beverage request ")
                            statusBIBONotFoundCount = 0 // BIBO B
                            globalDeviceAddressBnew = beacon.macAddress?.toString()!! //Beverage O data - (B)


                            if (filteredRssi > configBle5c.toInt() && utilSharedPref!!.getValueBoolean("beverage_check", false)) {
                                tempBLEAddress = result.device
                                tempBLEByteArray = result.scanRecord!!.bytes

                                smartLog("ble009", "* found B at ==> ${filteredRssi} :  ($configBle5c)")

                                scanLeDevice(false, "")
                                smartLog("ble009", "TICKET B::$readyBooleanCheck : $searchDevice" + result.device.address.toString() + result.device.name.toString())
                                statusService = true //BIBO 1.1 B


                                var wifiMac = ""
                                var breakOuterLoop = true



                                zigOverAllClientList.forEachIndexed { count,   clients ->

                                    //myLog("checkMacMismatch","forloop started ${count}")
                                    clients.major.forEachIndexed { index, biboA ->

                                        //myLog("checkMacMismatch","$index $majorMinor ${biboA==majorMinor}")

                                        if(biboA==majorMinor && breakOuterLoop){
                                            wifiMac = clients.biboGps[index]

                                            myLog("checkMacMismatch","$index took wifi mac from --- Major -> ${clients.major[index]} , Minor -> ${clients.minor[index]} , mac -> ${wifiMac}")
                                            breakOuterLoop = false // Set the flag to break the outer loop
                                            myLog("checkMacMismatch","we got the wifi mac so break the loop")


                                        }
                                    }
                                }

                                var countOfNewBeverage =0
                                bevListPurchasedataArray.forEachIndexed { index, item ->
                                    if(item.status==0){
                                        myLog("beverage_02","*"+item.status + item.name)
                                        countOfNewBeverage+=item.qty
                                    }
                                }
                                myLog("beverage_02","*"+ String.format("%03d", countOfNewBeverage))

                                //new format beverage
                                tempBleData = "201" + String.format("%03d", countOfNewBeverage)
                                smartLog("blevalidcheck","Beverage total count : $countOfNewBeverage")

                                when(Validationmode_B){
                                    // dual mode - online and offline
                                    0 ->{
                                        smartLog("blevalidcheck", "beverage validation mode dual connection mode = $Validationmode_B")
                                        if (wifiMac != "") {
                                            smartLog("mangologz", "mqtt init :dual mode: $wifiMac $tempBleData : type : $Validationmode_B $majorMinor")

                                            NfcValidation().startMqtt(applicationContextNew, wifiMac, tempBleData!!, 1, "", 0)
                                            // reReadyToConnectDevice(applicationContextNew)
                                        } else {
                                            smartLog("mangologz", "not registered dual mode")
                                            // reReadyToConnectDevice(applicationContextNew)
                                        }
                                    }
                                    1 ->{ //online mode
                                        smartLog("irfan00f1", "beverage validation mode online mode $wifiMac = $Validationmode_B")
                                        if (wifiMac != "") {
                                            smartLog("mangologz", "mqtt init :online mode: $wifiMac $tempBleData : type : $Validationmode_B $majorMinor")

                                            NfcValidation().startMqtt(applicationContextNew, wifiMac, tempBleData!!, 1, "", 0)
                                        } else {
                                            smartLog("mangologz", "not registered type online mode: $Validationmode_B : $tempBleData $majorMinor")
                                            //reReadyToConnectDevice(applicationContextNew)
                                        }

                                    }

                                    2,3 ->{ //offline mode ble
                                        smartLog("mangologz", "mqtt init : offline mode ble")
                                        //reReadyToConnectDevice(applicationContextNew)
                                    }

                                    else -> {
                                        if (wifiMac != "") {
                                            smartLog("mangologz", "else beverage validation mode default else dual mode = $Validationmode_B")
                                            NfcValidation().startMqtt(applicationContextNew, wifiMac, tempBleData!!, 1, "", 0)
                                            // reReadyToConnectDevice(applicationContextNew)
                                        } else {
                                            smartLog("mangologz", "else not registered")
                                            //  reReadyToConnectDevice(applicationContextNew)
                                        }
                                    }

                                }

                                // notification
                                util.showNotification(applicationContextNew, "Enjoy your $globalSecondaryValidationTitle from the counter", 123.toString())
                                utilSharedPref!!.save("beverageStatus", false)

                                try{
                                    val jsonObject = JSONObject()
                                    jsonObject.put("Userid", utilSharedPref!!.getValueInt("user_id"))
                                    jsonObject.put("Status", 1)
                                    jsonObject.put("MacAddress", tempBLEAddress)
                                    myLog("ble00921", "beverage request $jsonObject")

                                    val uploadLatLng1=ZigAPI.URL_BEVERAGE_VALIDATION
                                    val myBuilder: CronetEngine.Builder = CronetEngine.Builder(applicationContextNew)
                                    myBuilder.enableHttpCache(CronetEngine.Builder.HTTP_CACHE_IN_MEMORY, 100 * 1024)
                                    myBuilder.enableHttp2(true)
                                    myBuilder.enableQuic(true)
                                    val cronetEngine: CronetEngine = myBuilder.build()
                                    val executor: Executor = Executors.newSingleThreadExecutor()
                                    val requestBuilder: UrlRequest.Builder = cronetEngine.newUrlRequestBuilder(uploadLatLng1,
                                        BeverageOUTdataRequestCallback(applicationContextNew,jsonObject), executor
                                    )
                                    requestBuilder.setPriority(UrlRequest.Builder.REQUEST_PRIORITY_HIGHEST)
                                    requestBuilder.setHttpMethod("POST")
                                    requestBuilder.setUploadDataProvider(UploadDataProviders.create(jsonObject.toString().toByteArray()), executor)
                                    requestBuilder.addHeader("Content-Type", "application/json; charset=utf-8")
                                    val request: UrlRequest = requestBuilder.build()
                                    request.start()
                                }
                                catch (e:Exception){
                                    smartLog("ble00921", "beacon data catch 2 :: ${e.message}")
                                }


                                // send data
                                sendBIBOConnectionDetails(applicationContextNew, "", "Beverage")

                                try {
                                    val intent = Intent(applicationContextNew, BeverageTicket::class.java)
                                    val bundle = ActivityOptionsCompat.makeCustomAnimation(applicationContextNew, android.R.anim.fade_in, android.R.anim.fade_out).toBundle()
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    applicationContextNew.startActivity(intent, bundle)
                                } catch (e: Exception) {
                                    smartLog("ble009", "beverage redirect failed  : " + e.message.toString())
                                }
                                sendBeverageData() // IN BEVERAGE RANGE
                            }
                            else {
                                smartLog("ble009", "found B at *2: ${result.rssi} :  ($configBle5c) ::beverage  $filteredRssi else : ${utilSharedPref!!.getValueBoolean("beverage_check", false)}")
                            }
                        }
                        else if (beacon.major == 101){
                            ibeaconMacAddress = beacon.macAddress?.toString()!!
                            globalDeviceAddressOnew =  beacon.macAddress.toString()!!
                            smartLog("mango007", "init BIBO 1.1 O")
                            statusBIBONotFoundCount = 0
                            if (readyBooleanCheck) {
                                smartLog("mango007", "TICKET  O::$readyBooleanCheck $searchDevice$ibeaconMacAddress")
                                statusService           = true
                                globalDeviceAddress     = ibeaconMacAddress // result.device.address.toString() //ticket flow o
                            } else {
                                smartLog("mango007", "BIBO 1.1 O else")
                            }
                        }
                        //........

                    }
                }
                //.....................................................
            }
            catch (e:Exception){
                smartLog("ble009", "catch1 ${e.message}")
            }



        }
    }
    private fun findProximity(rssi: Int, message: String): String  {

        val distance = if (rssi < 0) { // filter rssi value
            val iRssi = abs(rssi)
            val iMeasurePower = abs(configMeasure)
            val power: Double = (iRssi - iMeasurePower) / (configTxPower * 2.0)
            val distance = 10.0.pow(power) * configDistance

            val result: String = when {
                distance < configImmediateFeet.toDouble() -> "Immediate"
                distance < configNear.toDouble() -> "Near"
                else -> "Far"
            }
            smartLog("zed007", "$result  :: $iRssi :: $distance :: $message $booleanCheckValidateOnce")
            return result

        }
        else {
            "Far"
        }
        smartLog("zed007", "FAR  :: $distance :: $message")
        return distance
    }


    /* private fun findProximity(rssi: Int, message: String): String  {

        val distance = if (rssi < 0) { // filter rssi value
            val iRssi = abs(rssi)
            val iMeasurePower = abs(configMeasure)
            val power: Double = (iRssi - iMeasurePower) / (configTxPower * 2.0)
            val distance = 10.0.pow(power) * configDistance

            val result: String = when {
                distance < configImmediateFeet.toDouble() -> "Immediate"
                distance < configNear.toDouble() -> "Near"
                else -> "Far"
            }
            smartLog("zed007", "$result  :: $iRssi :: $distance :: $message $booleanCheckValidateOnce")
            return result

        }
        else {
            "Far"
        }
        smartLog("zed007", "FAR  :: $distance :: $message")
        return distance
    }*/




    // find ticket status : new - activate, no tickets or active tickets (validated ticket - no action)
    private fun findTicketStatus() {
        //globalStartTime = System.currentTimeMillis()
        smartLog("zed007", "BIBO : have ticket :  $haveTickets => active:$haveActiveTicket  valid:$haveValidTicket  new:$haveNewTicket")

        if (haveTickets) {
            //.....................................................................
            if(haveActiveTicket){
                smartLog("zed007", "BIBO : active ticket")
                isScanOrConnection("already have active tickets")// 1 already have active tickets
            }
            else  if(haveNewTicket && ( haveTicketIdDefault>0)){
                smartLog("zed007", "BIBO : new ticket")
                utilSharedPref = SharedPreference(applicationContextNew)
                if(utilSharedPref!!.getValueBoolean("s_activate_api", true)){
                    utilSharedPref!!.save("s_activate_api", false) // need to check - logout clear?

                    try {
                        //smartLog("ble009", "no activate api")
                        ioScope.launch{
                            Log.d("zed007", "new to activated changed for auto validated : " + SeatDatabase.getDatabase(context = applicationContextNew).TicketDao().updateTicket(2, haveTicketIdDefault.toInt()))
                            isScanOrConnection("new - > active ticket") // 2 new - > active ticket
                            //.............................................................

                        }
                    }
                    catch (d1: java.lang.Exception) {
                        smartLog("zed007", "auto validated catch-" + d1.message)
                    }

                }
            }
            else if(haveValidTicket){
                smartLog("zed007", "BIBO : valid ticket")
                // readyBooleanCheck = true // VALIDATED TICKET
                // out data or stop scan
            }
            else{
                smartLog("zed007", "have some new tickets")
            }
        }
        else{
            isScanOrConnection("no tickets")// 3 no tickets
            smartLog("zed007", "no tickets :else ignore  ")
        }

    }

    //..............................................................................................is scan or connection
    //is scan or connection
    @SuppressLint("MissingPermission")
    private fun isScanOrConnection(message : String){

        if(validationModesStatus==2){
            smartLog("zed007", "callbackValidateFun connectGatt init offline:  $message")
            statusSendMessage = true //??
            disconnectRetry = 0 // offline
            gatt = tempBLEAddress!!.connectGatt(applicationContextNew, false, callbackBluetooth) //callbackValidateFun
        }
        else{
            // NfcValidation().startMqtt(applicationContextNew, "98:CD:AC:51:4A:E8", "301", 1, "1", 1)
            smartLog("zed007", "callbackValidateFun connectGatt init online :  $message")
            doValidation() // need to check again?
        }
    }

    // scan or connection - call this function do (notification, data sending, update active to validated status & send signal)
    private fun doValidation() {
        utilSharedPref = SharedPreference(applicationContextNew)

        ioScope.launch  {

            var findLastTicket=0
            //.........................list of 'in' data
            val listInData = arrayListOf<TicketPush>()
            listInData.clear()

            //.........................active ticket list
            val ticket = seatDatabase!!.TicketDao().selectActiveTicketOnly()
            if (ticket.isNotEmpty()) {

                var sTicket = ""
                var sExpire = ""
                var sRemaining = 0L
                findLastTicket = ticket.size

                val arrayList = ArrayList<String>()//Creating an empty arraylist
                arrayList.clear()

                var totalSum = 0
                ticketsType = "0"  //default
                for (i in ticket.indices) {
                    smartLog("zed007", "smart02 : Active id :: ${ticket[i].ticketId}  ::status :  ${ticket[i].status}")
                    findLastTicket--

                    arrayList.add(ticket[i].ticketId.toString())//Adding object in arraylist

                    try {
                        //.......................................
                        sTicket = ticket[i].ticketId.toString()
                        sExpire = ticket[i].expirydate
                        //expireDate = ticket[i].expirydate
                        //activationDate = ticket[i].expirydate
                        sRemaining = ticket[i].remainingTime
                        sendTicketId = ticket[i].ticketId.toString()
                        totalSum+= ticket[i].Reactivatecount!!



                        if(ticket[i].RouteId.lowercase().trim().contains("vip")){
                            myLog("check12","if worked")
                            ticketsType = "1"  //VIP
                        }




                        if(utilSharedPref!!.getValueInt("category_id")==20){
                            if (findLastTicket == 0) {
                                if(validationModesStatus==1){
                                    // online mqtt
                                    sendMessageToOnlineMqtt("express", "disconnect", sTicket, sExpire, sRemaining, ticket.size, arrayList, totalSum.toDecimalFormatBibo().toString(),totalSum)
                                }
                                else{
                                    //offline ble
                                    sendMessageToOfflineBluetooth("express", "disconnect", sTicket, sExpire, sRemaining, ticket.size, arrayList, totalSum.toDecimalFormatBibo().toString(),totalSum)
                                }
                            }
                        }
                        else{
                            if (findLastTicket == 0) {
                                smartLog("mangologz","total tickets sum: $totalSum")

                                if(validationModesStatus==1){
                                    sendMessageToOnlineMqtt("201", "disconnect", sTicket, sExpire, sRemaining, ticket.size, arrayList, totalSum.toDecimalFormatBibo().toString(),totalSum)
                                }
                                else{
                                    sendMessageToOfflineBluetooth("201", "disconnect", sTicket, sExpire, sRemaining, ticket.size, arrayList, totalSum.toDecimalFormatBibo().toString(),totalSum)
                                }


                            }
                        }



                        if(booleanCheckShowNotification){
                            util.showNotification(applicationContextNew, "* Your ticket : ${ticket[i].ticketId} is validated", ticket[i].ticketId.toLong().toString())
                        }

                        //.......................................store local db
                        try {

                            if(gpsLatitude=="0.0" || gpsLongitute=="0.0"){
                                gpsLatitude  = phoneLatitude
                                gpsLongitute = phoneLongitute
                            }

                            val pushObj = TicketPush(ticket[i].ticketId.toLong().toString() + "IN", gpsLongitute, gpsLatitude, "4", getCurrentDateTime(), token, ticket[i].ticketId.toLong().toString(), "IN", mBluetoothDeviceAddress, true, false, "4",utilSharedPref!!.getValueIntCOSI("zigClientID"),utilSharedPref!!.getValueInt("user_id").toLong())
                            listInData.add(pushObj)

                            //.......................................update local db : status : active -> valid
                            val dbStatus =seatDatabase!!.TicketDao().updateTicket(3, ticket[i].ticketId)
                            smartLog("zed007", "${ticket[i].ticketId}::" + dbStatus)

                            resetMyTicketOnceNew = true // update status 3 validated

                        } catch (e: Exception) {
                            e.printStackTrace()
                            smartLog("zed007", "smart02 : onCharacteristicChanged : catch s1: ${e.message.toString()}")
                        }

                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }

                    if (ticket.size - 1 == i) {
                        zedLog("mangologz", "smart02 :  validation done")
                    }
                }

                seatDatabase!!.TicketPushDao().insertMultipleListTicketPush(listInData)

                ioScope.launch {
                    try {
                        zedLog("zed007", "intent call")
                        val intent = Intent(applicationContextNew, MyTicketSuccess::class.java) // already have active ticket or new ticket make activate
                        intent.putExtra("expireTime", sRemaining)
                        intent.putExtra("expire", sExpire)
                        intent.putExtra("ticket_id", sTicket)
                        // intent.putExtra("ticket_count", totalCount.toString())
                        intent.putExtra("ticket_count", "1")
                        intent.putExtra("scanStatus", "true")
                        intent.putExtra("validated", "true")
                        intent.putExtra("validatedDate", getCurrentDateTimeValidated().toString())
                        intent.putExtra("ticketList", arrayList)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        applicationContextNew.startActivity(intent)
                    }
                    catch (e: Exception) {
                        smartLog("zed007", "intent catch " + e.message.toString())
                    }
                }



                delay(5000)
                //........................................
                val ticketIn        = seatDatabase!!.TicketPushDao().selectTicketPushALimit() // BOTH ILLEGAL & IN
                val gsonObject          = Gson()
                val jsonObject  = gsonObject.toJson(ticketIn)
                val biboResponse        = "{\"list\":$jsonObject}"
                try {
                    val biboJsonObject = JSONObject(biboResponse)
                    booleanCheckIn = false // smart service - cronet call
                    try{
                        val uploadLatLng = ZigAPI.URL_TICKET_ADD_LOG+"?autovalidate=$biboInOutAutoValidate"

                        smartLog("zed007", "008 in request:$biboJsonObject $uploadLatLng")


                        val myBuilder: CronetEngine.Builder = CronetEngine.Builder(applicationContextNew)
                        myBuilder.enableHttpCache(CronetEngine.Builder.HTTP_CACHE_IN_MEMORY, 100 * 1024)
                        myBuilder.enableHttp2(true)
                        myBuilder.enableQuic(true)
                        val cronetEngine: CronetEngine = myBuilder.build()
                        val executor: Executor = Executors.newSingleThreadExecutor()
                        val requestBuilder: UrlRequest.Builder = cronetEngine.newUrlRequestBuilder(uploadLatLng,
                            INdataRequestCallback(applicationContextNew), executor
                        )
                        requestBuilder.setPriority(UrlRequest.Builder.REQUEST_PRIORITY_HIGHEST)
                        requestBuilder.setHttpMethod("POST")
                        requestBuilder.setUploadDataProvider(UploadDataProviders.create(biboJsonObject.toString().toByteArray()), executor)
                        requestBuilder.addHeader("Content-Type", "application/json; charset=utf-8")
                        val request: UrlRequest = requestBuilder.build()
                        request.start()
                    }catch (e:Exception){
                        smartLog("zed007", "beacon data catch 1222 :: ${e.message}")
                    }
                } catch (e: Exception) {
                    SmartService().smartLog("zed007", "IN data::catch1211:${e.message}")
                }
                //........................................

            }
            else if(activeTickets == 0 && validatedTickets == 0){


                if(booleanCheckValidateIllegalOnce){

                    if ((utilSharedPref!!.getValueBoolean("firstTimeIllegalEntryStatus", true))) {
                        //.........................set flag false
                        utilSharedPref!!.save("firstTimeIllegalEntryStatus",false)
                        booleanCheckValidateIllegalOnce = false //callbackIllegalFun once hit

                        sendSingleMessageIllegalNew()//301

                        //.........................send notification
                        util.showNotification(applicationContextNew, "You do not have any active tickets.", 0.toString())

                        //.........................push data
                        try {
                            if(gpsLatitude=="0.0" || gpsLongitute=="0.0"){
                                gpsLatitude  = phoneLatitude
                                gpsLongitute = phoneLongitute
                            }

                            val push = TicketPush("0${getCurrentDateTime()}", gpsLongitute, gpsLatitude, "0", getCurrentDateTime(), token, 0.toString(), "Illegal", mBluetoothDeviceAddress, true, false,"4",utilSharedPref!!.getValueIntCOSI("zigClientID"),utilSharedPref!!.getValueInt("user_id").toLong())
                            val result = seatDatabase!!.TicketPushDao().insertTicketPush(push)
                            zedLog("zed007", "illegal room  : $result")

                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                        //.........................disconnect
                        finally {
                            //delay(1000)
                            //.........................redirect
                            try {
                                val intent = Intent(applicationContextNew, MyTicketFailure::class.java)
                                val bundle = ActivityOptionsCompat.makeCustomAnimation(applicationContextNew, android.R.anim.fade_in, android.R.anim.fade_out).toBundle()
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                applicationContextNew.startActivity(intent, bundle)
                            } catch (e: Exception) {
                                zedLog("zed007", "illegal redirect failed  : " + e.message.toString())
                            }
                        }
                    }
                    else{
                        smartLog("zed007", "firstTimeIllegalEntryStatus wait get ticket load ...")
                    }

                }
                else{
                    smartLog("zed007", "duplicate illegal ! $booleanCheckValidateIllegalOnce")
                }



            }
            else{
                smartLog("mangologz001", "callbackValidateFun() : -mac address verified...else ")
            }
        }
    }

    //..............................................................................................ibeacon connection method
    private val callbackBluetooth = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (ActivityCompat.checkSelfPermission(applicationContextNew, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return
            }
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                statusAfterConnect = true  // connected
                smartLog("zed007", "BIBO A is connected")
                smartLog("ble009", "callback new - BIBO A is connected...")
                smartLog("mangologz", "callback new - BIBO A is connected...")
                if (!gatt.discoverServices()) {
                    smartLog("ble009", "callback new - Failed to start discovering services!")
                    smartLog("mangologz", "callback new - Failed to start discovering services!")
                }
            }
            else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                statusAfterConnect = false  //  dis-connected
                smartLog("zed007", "BIBO A is dis-connected $disconnectRetry")

                smartLog("ble009", "callback new - BIBO A is dis-connected...")
                smartLog("mangologz", "callback new - BIBO A is dis-connected...")

                disconnectRetry++
                //if (tempBLEAddress!!.address == mBluetoothDeviceAddress && gatt != null && mBluetoothDeviceAddress != null && statusSendMessage) {
                if (gatt != null && mBluetoothDeviceAddress != null && statusSendMessage) {
                    if (gatt.connect() && disconnectRetry<3) {
                        smartLog("mangologz", "callback new :  01")
                    } else {
                        gatt.close()
                        gatt.disconnect()
                        smartLog("mangologz", "callback new : 02")
                    }
                }

            }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (ActivityCompat.checkSelfPermission(applicationContextNew, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                smartLog("zed007", "callback new - Service discovery completed!")
                smartLog("mangologz", "callback new - Service discovery completed!")
            }
            else {
                smartLog("ble009", "callback new - Service discovery failed with status: $status")
                smartLog("mangologz", "callback new - Service discovery failed with status: $status")
            }
            try {
                tx = gatt.getService(UART_UUID).getCharacteristic(TX_UUID)
                rx = gatt.getService(UART_UUID).getCharacteristic(RX_UUID)
                if (!gatt.setCharacteristicNotification(rx, true)) {}
                if (rx!!.getDescriptor(CLIENT_UUID) != null) {
                    val desc = rx!!.getDescriptor(CLIENT_UUID)
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    if (!gatt.writeDescriptor(desc)) { }
                }
            }
            catch (e: Exception) {
                smartLog("mangologz", "callback new - parseUUIDs b7- tx/rx : " + e.message)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)

            //characteristic.getStringValue(0).contains("#")
            if (statusSendMessage && booleanCheckValidateGattWrite) {
                booleanCheckValidateGattWrite = false
                smartLog("zed007", "acknowledgement confirmed ${characteristic.getStringValue(0)}  :: ${getTime()}")
                doValidation()
            }

            /*

                        if (characteristic.getStringValue(0).isNotEmpty() && characteristic.getStringValue(0).contains("#")) {
                            if(statusAfterConnect) {
                                statusAfterConnect = false

                                smartLog("ble009", "callback new - after connect.")

                                if(booleanCheckAvoideFailureDuplicate){
                                    booleanCheckAvoideFailureDuplicate =false
                                    val pattern = Pattern.compile("^(\\w)#(.{17})#$")
                                    val matcher = pattern.matcher(characteristic.getStringValue(0))
                                    if (matcher.find()) {

                                        smartLog("mangologz", "find")



                                        try {
                                            beaconCommand = matcher.group(1).toString()
                                            mBluetoothDeviceAddress = matcher.group(2).toString()
                                            smartLog("ble009", "callback new -beacon:$beaconCommand & address:$mBluetoothDeviceAddress")
                                            smartLog("mangologz", "callback new -beacon:$beaconCommand & address:$mBluetoothDeviceAddress")
                                        }
                                        catch (e: Exception) {
                                            smartLog("ble009", "callback new -verify device is failed : 2021:${e.message}")
                                            smartLog("mangologz", "callback new -verify device is failed : 2021:${e.message}")
                                        }

                                        //................................................. peripheral device name check
                                        if (beaconCommand == "A"||beaconCommand == "C"||beaconCommand == "B") {

                                            ioScope.launch {
                                                sendSingleMessageBluetooth("201", "single","201")//recheck - internal - GET FROM MQTT
                                                try {
                                                    disconnectNew(gatt, "callback new -gatt is disconnected : on illegal. ", "false")
                                                }
                                                catch (e: Exception) {
                                                    smartLog("ble009", "callback new -illegal gatt failed : " + e.message)
                                                }
                                            }
                                        }
                                        else {
                                            smartLog("ble009", "smart02 : callback new-mismatch-$matcher")
                                        }

                                    }
                                    else{
                                        smartLog("ble009", "smart02 : callback new -not match..")
                                    }
                                }
                                else{
                                    smartLog("ble009", "callback new - else2 $booleanCheckAvoideFailureDuplicate")
                                }
                            }
                            else{
                                smartLog("ble009", "callback new - after connect else.")
                                smartLog("validcheck", "callback new - after connect else.")
                            }
                        }
            */

        }
    }
    //..............................................................................................send message
    private fun sendSingleMessageIllegalNew() {
        smartLog("zed007","sendSingleMessageIllegalNew init")
        var wifiMac=""
        var serialNo=""
        var breakOuterLoop = true
        zigOverAllClientList.forEachIndexed { count,   clients ->

            //myLog("checkMacMismatch","forloop started ${count}")
            clients.major.forEachIndexed { index, biboA ->

                //myLog("checkMacMismatch","$index $majorMinor ${biboA==majorMinor}")

                if(biboA==majorMinor && breakOuterLoop){
                    wifiMac=clients.biboGps[index]
                    serialNo=clients.serialNo[index]

                    myLog("checkMacMismatch","$index took wifi mac from --- Major -> ${clients.major[index]} , Minor -> ${clients.minor[index]} , mac -> ${wifiMac}")
                    breakOuterLoop = false // Set the flag to break the outer loop
                    myLog("checkMacMismatch","we got the wifi mac so break the loop")


                }
            }
        }
        tempBleData="301"

        if(validationModesStatus == 2){
            smartLog("zed007","offline method $validationModesStatus : ${getTime()}")

            ioScope.launch {
                if (ActivityCompat.checkSelfPermission(applicationContextNew, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    return@launch
                }

                if (tx == null || tempBleData!!.isEmpty()) {
                    smartLog("zed007", "no device or message to send")
                    return@launch
                }

                tx!!.value = tempBleData!!.toByteArray(Charset.forName("UTF-8"))

                if (gatt!!.writeCharacteristic(tx)) {
                    booleanCheck=false // success sendSingleMessage
                    statusSendMessage=false // success sendSingleMessage
                    smartLog("zed007", "send ------------------->:: $tempBleData : ${getTime()}")
                    delay(1000) // if data loss means remove this
                    gatt?.disconnect()
                    gatt?.close()
                    smartLog("zed007", "...........................................")
                    smartLog("zed007", "")
                    smartLog("zed007", "")
                    smartLog("zed007", "")
                    smartLog("zed007", "")
                    SmartService().sendLogToGoogleSheet(applicationContextNew)

                }
                else {
                    smartLog("zed007", "Couldn't write TX characteristic!")
                    sendBIBOConnectionDetails(applicationContextNew,"","illegal failed")
                }
            }

        }
        else{
            // online
            if(wifiMac!=""){
                smartLog("zed007","online method $validationModesStatus")
                NfcValidation().startMqtt(applicationContextNew, wifiMac, tempBleData!!, 1, serialNo, 0)
            }
            else{
                smartLog("zed007","---xxx--- no mac address ---xxx---")
            }
        }
    }

    private fun sendMessageToOfflineBluetooth(message: String, status: String, sTicket: String, sExpire: String, sRemaining: Long, size: Int, arrayList: java.util.ArrayList<String>, totalSum: String, totalCount:Int) {//message: String, s: String, totalSum: String


        if (flagSendNewFormat==2){
            smartLog("mangologz","validation mode = $validationModesStatus")
            tempBleData= "$message$totalSum#${nameNewFormat(utilSharedPref!!.getValueString("user_first_name").toString())}#$ticketsType#$sendTicketId#${convertToBeaconFormat(getCurrentDateTimeForBeacon())}#${convertToBeaconFormat(getExpireDate(getCurrentDateTimeForBeacon()))}"
            myLog("usernameTest 4", nameNewFormat(utilSharedPref!!.getValueString("user_first_name").toString()))
            myLog("dateCheck activation", convertToBeaconFormat(getCurrentDateTimeForBeacon()))
            myLog("dateCheck expiry", convertToBeaconFormat(getExpireDate(getCurrentDateTimeForBeacon())))
        }
        else{
            tempBleData="$message$totalSum" //201 validation
        }

        smartLog("zed007", "send message init  ${getTime()}")

        ioScope.launch {
            if (tx == null || tempBleData!!.isEmpty()) {
                smartLog("zed007", "send message $tempBleData: failed do nothing : there is no device or message to send.")
                smartLog("validcheck", "send message $tempBleData: failed do nothing : there is no device or message to send.")
                return@launch
            }

            //tx!!.value = tempBleData!!.toByteArray(Charset.forName("UTF-8"))
            tx!!.value = tempBleData!!.toByteArray(Charset.forName("UTF-8"))

            if (ActivityCompat.checkSelfPermission(applicationContextNew, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return@launch
            }
            if (gatt!!.writeCharacteristic(tx)) {
                booleanCheck=false // success sendSingleMessage
                statusSendMessage=false // success sendSingleMessage
                smartLog("zed007", "send ------------------->:: $tempBleData : ${getTime()}")
                smartLog("mangologz", "bluetooth sent $tempBleData")
                delay(1000) // if data loss means remove this
                /*  try {
                      mLEScanner!!.stopScan(highScanCallback)
                  }catch (e:Exception){}*/
                gatt?.disconnect()
                gatt?.close()

                smartLog("zed007", "...........................................")
                smartLog("zed007", "")
                smartLog("zed007", "")
                smartLog("zed007", "")
                smartLog("zed007", "")


                SmartService().sendLogToGoogleSheet(applicationContextNew)
            }
            else {
                smartLog("zed007", "Couldn't write TX characteristic!")
                smartLog("mangologz", "Couldn't write TX characteristic!")
                sendBIBOConnectionDetails(applicationContextNew,"","Bluetooth Auto -Failed")
            }
        }




    }
    private fun sendMessageToOnlineMqtt(message: String, status: String, sTicket: String, sExpire: String, sRemaining: Long, size: Int, arrayList: java.util.ArrayList<String>, totalSum: String, totalCount:Int) {
        smartLog("zed007","send message init")
        myLog("checkMacMismatch","\n\n\n\n sendMultipleMessage ${zigOverAllClientList}")
        myLog("checkMacMismatch","size while sending ${zigOverAllClientList.size}")

        var wifiMac=""
        var serialNo=""
        var breakOuterLoop = true



        zigOverAllClientList.forEachIndexed { count,   clients ->

            //myLog("checkMacMismatch","forloop started ${count}")
            clients.major.forEachIndexed { index, biboA ->

                //myLog("checkMacMismatch","$index $majorMinor ${biboA==majorMinor}")

                if(biboA==majorMinor && breakOuterLoop){
                    wifiMac=clients.biboGps[index]
                    serialNo=clients.serialNo[index]

                    myLog("checkMacMismatch","$index took wifi mac from --- Major -> ${clients.major[index]} , Minor -> ${clients.minor[index]} , mac -> ${wifiMac}")
                    breakOuterLoop = false // Set the flag to break the outer loop
                    myLog("checkMacMismatch","we got the wifi mac so break the loop")


                }
            }
        }
        //newFormat
        myLog("zed007","mac found -> wifiMac ${wifiMac}")

        if (flagSendNewFormat==2){
            smartLog("mangologz","validation mode = $validationModesStatus")
            tempBleData= "$message$totalSum#${nameNewFormat(utilSharedPref!!.getValueString("user_first_name").toString())}#$ticketsType#$sendTicketId#${convertToBeaconFormat(getCurrentDateTimeForBeacon())}#${convertToBeaconFormat(getExpireDate(getCurrentDateTimeForBeacon()))}"
            myLog("usernameTest 4", nameNewFormat(utilSharedPref!!.getValueString("user_first_name").toString()))
            myLog("dateCheck activation", convertToBeaconFormat(getCurrentDateTimeForBeacon()))
            myLog("dateCheck expiry", convertToBeaconFormat(getExpireDate(getCurrentDateTimeForBeacon())))
        }
        else{
            tempBleData="$message$totalSum" //201 validation
        }


        // 1 SEC
        ioScope.launch(Dispatchers.IO) {
            // MQTT call
            NfcValidation().startMqtt(applicationContextNew, wifiMac, tempBleData!!, 1, serialNo, totalCount)
        }

        // 2 SEC
        /*ioScope.launch(Dispatchers.IO) {
            // MQTT call
            NfcValidation().startMqtt(applicationContextNew, wifiMac, tempBleData!!, 1, serialNo, totalCount)
        }*/

    }
    //..............................................................................................ibeacon


    //..............................................................................................ibeacon util
    fun getTime(): String {
        val currentDate = Date()
        val dateFormat = SimpleDateFormat("mm:ss.sss")
        return dateFormat.format(currentDate)
    }





















    //..............................................................................................clack mass
    private val highScanCallbackClackmass = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {

            if (ActivityCompat.checkSelfPermission(
                    applicationContextNew,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                return
            }
            if (result.device.name != null && result.device.address != null){


                if (result.device.name.contains("ZIG_")) {
                    val parts = result.device.name.split("_")
                    val limaValue = parts.getOrNull(0)
                    myLog("limadata3", "scan found : ${result.device.name}")
                    scanLeDevice(false,"clackmass")

                    myLog("ble0096", "lime remove *1* "+limaValue.toString())


                    if ("BIBO 1.1 O" == result.device.name.toString() || "ZIG" == limaValue) {
                        globalDeviceAddressOnewCount = 1
                        myLog("ble0096", "login status true")
                        scanLeDevice(false,"clackmass")
                        myLog("limadata", "BIBO O clackmass:: " + result.device.address.toString() + result.device.name.toString())
                        statusService = true // BIBO 1.1 O clakamas
                        globalDeviceAddress = result.device.address.toString() //clakamas 0
                        globalDeviceAddressOnew = result.device.address.toString() //CLACKMASS

                        if(globalClientType==4){
                            biboDataLima(
                                gpsLatitude,
                                gpsLongitute,
                                applicationContextNew,
                                globalDeviceAddressOnew,
                                "OUT"
                            ) //OUT
                        }

                    }
                    else{
                        myLog("ble0096", "lime remove1: "+ result.device.name.toString())
                    }

                }
                else {

                    myLog("limadata3", "scanning device: ${result.device.name}")


                    if ("BIBO 1.1 O" == result.device.name.toString()) {
                        //scanLeDevice(false,"clackmass")
                        globalDeviceAddressOnewCount = 1
                        myLog("ble0096", "login status true")
                        scanLeDevice(false, "")
                        myLog("ble0096", "BIBO O clackmass:: " + result.device.address.toString() + result.device.name.toString())
                        statusService = true // BIBO 1.1 O clakamas
                        globalDeviceAddress = result.device.address.toString() //clakamas 0
                        globalDeviceAddressOnew = result.device.address.toString() //CLACKMASS
                    }
                    else{
                        myLog("ble0096", "lime remove2: "+ result.device.name.toString())
                    }
                    //println("Underscore character not found in the input string.")
                }


            }

        }
    }
    fun sendBeverageData(){
       /* val beverageObject = JSONObject()
        try {
            //  beaverageObject.put("VisitorID", VisitorID)
            beverageObject.put("BeaconId", globalDeviceAddressBnew)
            beverageObject.put("UserID", utilSharedPref!!.getValueInt("user_id"))
            beverageObject.put("Datetime", getCurrentUTC())
            beverageObject.put("Appdate", getCurrentUTC())
            beverageObject.put("ClientID", utilSharedPref!!.getValueIntCOSI("zigClientID").toString())
            beverageObject.put("emailid", utilSharedPref!!.getValueString("user_email"))
            beverageObject.put("Phone", utilSharedPref!!.getValueString("user_phone"))
            //  beverageObject.put("UserName", utilSharedPref!!.getValueString("user_name").toString())

            if(utilSharedPref!!.getValueString("user_first_name")!="" && utilSharedPref!!.getValueString("user_last_name")!=""){
                beverageObject.put("UserName", utilSharedPref!!.getValueString("user_first_name").toString()+" "+utilSharedPref!!.getValueString("user_last_name").toString())
            }
            else{
                beverageObject.put("UserName", utilSharedPref!!.getValueString("user_name").toString())
            }

            myLog("test0001",""+beverageObject)
            myLog("zed007",""+beverageObject)

            val jsonReq = JsonObjectRequest(
                Request.Method.POST,
                getRootUrlV3() + "api/Visitor",
                beverageObject,
                {
                    globalDeviceAddressBnew = ""
                    myLog("test0001","y")

                },
                {
                    globalDeviceAddressBnew = ""
                    myLog("test0001","n")

                })
            MySingleton.getInstance(applicationContextNew).addToRequestQueue(jsonReq)
        }
        catch (e:java.lang.Exception){
        }*/
    }














    fun getExpireDate(input:String):String{
        try {
            val inDF: SimpleDateFormat =  when(input.length){

                23 -> {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH)
                }
                19 -> {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
                }
                21 -> {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S", Locale.ENGLISH)
                }
                22 -> {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SS", Locale.ENGLISH)
                }
                else -> {
                    if(input.contains(".")) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH)
                    }else{
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
                    }
                }
            }
            val aDate      = inDF.parse(input)
            val outDF             = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            outDF.timeZone        = TimeZone.getTimeZone("America/New_York")
            val strDateOut = outDF.format(aDate!!)
            val format            = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            format.timeZone       = TimeZone.getTimeZone("America/New_York")
            val d          = format.parse(strDateOut)
            val c       = Calendar.getInstance()
            c.time                = d!!
            c.add(Calendar.HOUR, 3)
            val dateFormat        = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val currentDatePlusOne = c.time
            return dateFormat.format(currentDatePlusOne).toString()
        }
        catch (e2: Exception){
            myLog("expire01", "catch ${e2.message}")
            // worst case handling : 2
            return ""
        }
    }
    fun convertToBeaconFormat(input : String):String {
        try {
            myLog("expire0123", "iniy $input")
            val inDF: SimpleDateFormat =  when(input.length){

                23 -> {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH)
                }
                19 -> {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
                }
                21 -> {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S", Locale.ENGLISH)
                }
                22 -> {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SS", Locale.ENGLISH)
                }
                else -> {
                    if(input.contains(".")) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH)
                    }else{
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
                    }
                }
            }
            val aDate      = inDF.parse(input)
            val outDF             = SimpleDateFormat("MM-dd-yyyy HH:mm", Locale.getDefault())
            outDF.timeZone        = TimeZone.getTimeZone("America/New_York")
            val strDateOut = outDF.format(aDate!!)
            myLog("expire0123",strDateOut)
            return strDateOut

        }
        catch (e2: Exception){
            myLog("expire0123", "catch ${e2.message}")
            // worst case handling : 2

        }
        return ""
    }







    fun reReadyToConnectDevice(context: Context) {
    }
    //..............................................................................................

    fun ecolaneLogReport(context: Context,message :String){
    } // * payment failure zig report log



    fun linesLogReport(context: Context,message :String) {


    } // * payment failure zig report log




    fun reportBibo(context: Context,message :String) {


    } // * payment failure zig report log
    // * payment failure zig report log
    fun zigReport(context: Context,message :String) {


    } // * payment failure zig report log








    fun zigModeReport(context: Context,mode: String) {


    }


    // * debug log
    fun apiLog(tag: String, message: String) {
        try{
            apiLog.append(message+"\n")
        }catch (e:Exception){}

    }

    fun sendLogToGoogleSheet(context: Context) {
        try {
            val url = "https://docs.google.com/forms/d/1StgtwUNIb3PUvvizbOVtDehY0RRq_wZcql1iXsCvt4Q/formResponse"
            val stringRequest = object : StringRequest(
                Method.POST, url, Response.Listener {
                    //This code is executed if the server responds, whether or not the response contains data.
                    //The String 'response' contains the server's response.
                    myLog("ble009", "api call : sendApiLogResponseTime() success")
                    sbLog.clear()
                    apiLog.clear()
                }, Response.ErrorListener
                //Create an error listener to handle errors appropriately.
                {
                }) {
                override fun getParams(): Map<String, String> {
                    val parmas: MutableMap<String, String> = HashMap()
                    parmas["entry.1496847096"] = apiLog.toString()
                    parmas["entry.678169171"] = sbLog.toString()
                    return parmas
                }
            }
            val socketTimeOut = 50000 // u can change this .. here it is 50 seconds
            val retryPolicy: RetryPolicy = DefaultRetryPolicy(socketTimeOut, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
            stringRequest.retryPolicy = retryPolicy
            MySingleton.getInstance(context).addToRequestQueue(stringRequest)
        }catch (e:Exception){

        }

    }





    // * debug log
    fun smartLog(tag: String, message: String) {
        // Log.d("applex01", "smartLog : $message")
        try{
            val currentDateTime =  SimpleDateFormat("HH:mm:ss.SSS::",Locale.getDefault())
            val etTimeZone2 = TimeZone.getTimeZone("America/New_York")
            currentDateTime.timeZone =  etTimeZone2
            //testingModeItemModels.add(MyData("ble","${currentDateTime.format(Date())}    :: $tag  -  "+message+"\n"))
        }catch (e:Exception){}

        try{
            val currentDateTime =  SimpleDateFormat("HH:mm:ss.SSS::",Locale.getDefault())
            val etTimeZone2 = TimeZone.getTimeZone("America/New_York")
            currentDateTime.timeZone =  etTimeZone2
            if(tag.contains("mangologz") || tag.contains("mangologz001") || tag.contains("orangelogz") || tag.contains("zed007")){
                // sbLog.append("${currentDateTime.format(Date())}    :: $tag  -  "+message+"\n")
                if(message.contains(" , ")){
                    sbLog.append(message)
                }
                else{
                    sbLog.append("${currentDateTime.format(Date())}-"+message+"\n")
                }
            }


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

            /*  try{
                  testingModeItemModels.add(MyData(tag,message))
              }catch (e:Exception){}*/

        }catch (e: Exception){
            myLog("ble009", "smartLog() catch ${e.message}")
        }
    }



    //..............................................................................................
    // foreground service
    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun startOreoForegroundNotification(message: String) {
        try{
            val intent = Intent("action.cancel.notification")
            intent.setClass(this, CancelNotificationReceiver::class.java)

            val pi2: PendingIntent= if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.getBroadcast(this, 3, intent, PendingIntent.FLAG_IMMUTABLE)
            } else {
                PendingIntent.getBroadcast(this, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            }

            val notificationId = BuildConfig.APPLICATION_ID
            val channelName = "${getString(R.string.app_name)} "
            val chan = NotificationChannel(notificationId, channelName, NotificationManager.IMPORTANCE_NONE)
            chan.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
            val iconColor = ContextCompat.getColor(this, R.color.brand_primary) // Replace with your desired color resource ID
            val notificationBuilder = NotificationCompat.Builder(this, notificationId)
            val notification = notificationBuilder.setOngoing(true)
                // .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationManager.IMPORTANCE_NONE)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setShowWhen(false)
                .setStyle(NotificationCompat.InboxStyle().setSummaryText(message))
                //.setColor(ContextCompat.getColor(this, R.color.brand_primary))
                .addAction(0, "Close", pi2)


                .setSmallIcon(R.drawable.ic_ticket)
                .setColorized(true) // Enable colorization of the small icon
                .setColor(iconColor)// Set the color of the small icon

                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
                try{
                    startForeground(2, notification,FOREGROUND_SERVICE_TYPE_LOCATION)
                }catch (e:Exception){}
            }else{
                startForeground(2, notification)
            }
        }catch (e:Exception){

        }
    }
    private fun startNotification(message: String) {
        val intent = Intent("action.cancel.notification")
        intent.setClass(this, CancelNotificationReceiver::class.java)

        val pi2: PendingIntent= if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(this, 3, intent, PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getBroadcast(this, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        // lower version
        val notification1 = NotificationCompat.Builder(this@SmartService)
            .addAction(0,"Close", pi2)
            .setPriority(NotificationManagerCompat.IMPORTANCE_NONE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentTitle("${getString(R.string.app_name)} ")
            .setTicker("${getString(R.string.app_name)} ")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(message)
            .build()
        startForeground(1, notification1)
    }

    fun getTicketFailure(context: Context,errorString: String) {


    }


}

//...............................................................................................
//...............................................................................................server log - taken trip, bibo data
fun sendTripData(context: Context, appRequestUsage: String){
    if(tripPlannerBoolean){
        try{
            myLog("ble009","sendTripData() init $appRequestUsage")
            val jsonObj=JSONObject(appRequestUsage)
            val url="${getRootUrl()}api/Application/AddTripPlanData"
            val utilSharedPref = SharedPreference(context)

            val jsonReq = JsonObjectRequest(Request.Method.POST, url, jsonObj, {
                myLog("ble009","Hit success${it}")
                utilSharedPref.save("locationDetail","")
                utilSharedPref.save("reachedSource",false)
                utilSharedPref.save("reachedDestination",false)
                myLog("ble009","sendTripData() success")
            }, {
                myLog("ble009","sendTripData() failed")
            })
            MySingleton.getInstance(context).addToRequestQueue(jsonReq)
        }catch (e:java.lang.Exception){
            myLog("ble009", "sendTripData() catch 1:"+e.message.toString())
        }
    }
    else{
        myLog("apple03","status :$tripPlannerBoolean")
    }
}
fun biboData(lat: String, lng: String, context: Context, globalDeviceAddress: String, newMessage: String){
    val utilSharedPref = SharedPreference(context)

    if(globalDeviceAddressOnew!=""){
        try {
            if(utilSharedPref.getValueString("user_token")!="") {
                try {
                    val overallJsonObject = JSONObject()
                    val jsonObject = JSONObject()
                    jsonObject.put("latitude", lat)
                    jsonObject.put("longitude", lng)

                    if(globalClientType==10 || newMessage==""){
                        jsonObject.put("ticketid", 0)
                        jsonObject.put("routeid", "Out Data")
                    }
                    else{
                        jsonObject.put("ticketid", utilSharedPref.getValueInt("user_id").toString())
                        jsonObject.put("routeid", "1")
                    }

                    jsonObject.put("accesstoken", utilSharedPref.getValueString("user_token"))
                    jsonObject.put("message", inOutData)
                    jsonObject.put("date", getCurrentDateTime())
                    jsonObject.put("userid", utilSharedPref.getValueInt("user_id").toString())
                    jsonObject.put("beaconid",globalDeviceAddressOnew)
                    jsonObject.put("tripid", "1077")
                    jsonObject.put("IsDriver", false)
                    jsonObject.put("IsBibo", true)
                    jsonObject.put("Clientid", utilSharedPref.getValueIntCOSI("zigClientID"))
                    val jsonArray=JSONArray()
                    jsonArray.put(jsonObject)
                    overallJsonObject.put("List",jsonArray)
                    myLog("limadata",overallJsonObject.toString())


                    limaLogTest(context,""+overallJsonObject.toString(),utilSharedPref.getValueString("user_name").toString())

                    if(lat.length>=4){
                        //val url="${ZigAPI.ROOT_URL}api/Beacon/Add"
                        /*val url="https://zig-web.com/Zigsmartv3Lima/api/Beacon/Add"
                        val jsObjRequest = JsonObjectRequest(Request.Method.POST, url, overallJsonObject, { response ->
                            myLog("shuttle bibo data",response.toString())
                            inOutData = "OUT"
                            myLog("limadata","biboData() api success")
                            globalDeviceAddressBnew=""

                            if(globalDeviceAddressOnewCount>=2){
                                globalDeviceAddressOnew = ""
                                myLog("limadata","clear the data now")
                            }
                            else{
                                myLog("limadata","device count is still less than 2 : count -> $globalDeviceAddressOnewCount")
                                myLog("limadata","So send the data")
                            }


                        }) {

                            try{
                                limaLogTest(context,""+it.toString(),utilSharedPref.getValueString("user_name").toString())
                            }catch (e:Exception){}

                            myLog("limadata","biboData() api error")
                        }

                        //to avoid out of memory & handle time out issue
                        jsObjRequest.retryPolicy = DefaultRetryPolicy(60000 * 2, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
                        MySingleton.getInstance(context).addToRequestQueue(jsObjRequest)*/






                        try{
                            val startTime = System.currentTimeMillis()
                            val uploadLatLng=getRootUrlV3() + "api/Beacon/Add"
                            val myBuilder: CronetEngine.Builder = CronetEngine.Builder(context)
                            myBuilder.enableHttpCache(CronetEngine.Builder.HTTP_CACHE_IN_MEMORY, 100 * 1024)
                            myBuilder.enableHttp2(true)
                            myBuilder.enableQuic(true)
                            val cronetEngine: CronetEngine = myBuilder.build()
                            val executor: Executor = Executors.newSingleThreadExecutor()
                            val requestBuilder: UrlRequest.Builder = cronetEngine.newUrlRequestBuilder(uploadLatLng,
                                OUTdataRequestCallback(context,startTime), executor
                            )
                            requestBuilder.setPriority(UrlRequest.Builder.REQUEST_PRIORITY_HIGHEST)
                            requestBuilder.setHttpMethod("POST")
                            requestBuilder.setUploadDataProvider(UploadDataProviders.create(overallJsonObject.toString().toByteArray()), executor)
                            requestBuilder.addHeader("Content-Type", "application/json; charset=utf-8")
                            val request: UrlRequest = requestBuilder.build()
                            request.start()
                        }
                        catch (e:Exception){
                            limaLogTest(context,"111"+ {e.message}, utilSharedPref.getValueString("user_name").toString())
                            myLog("limadata1", "beacon data catch 2 :: ${e.message}")
                        }






                    }
                    else{
                        myLog("limadata","biboData() invalid lat lng")
                    }
                } catch (e: JSONException) {
                    limaLogTest(context,"222"+e.toString(),utilSharedPref.getValueString("user_name").toString())
                    myLog("limadata", "biboData() catch 1:"+e.message.toString())
                }
                catch (e: Exception) {
                    limaLogTest(context,"333"+e.toString(),utilSharedPref.getValueString("user_name").toString())
                    myLog("limadata", "biboData() catch 2:"+e.message.toString())
                }
            }
        }
        catch (e:java.lang.Exception){
            myLog("limadata", "biboData() catch 2:"+e.message.toString())
        }
    }
    else{
        myLog("limadata", "biboData() no mac address: *")
    }


}


fun biboDataLima(lat: String, lng: String, context: Context, globalDeviceAddress: String, newMessage: String){
    val utilSharedPref = SharedPreference(context)
    myLog("limadata3", "api called $globalDeviceAddress")
    biboDataLimaTest++

    if(globalDeviceAddress!=""){
        try {
            if(utilSharedPref.getValueString("user_token")!="") {
                try {
                    val overallJsonObject = JSONObject()
                    val jsonObject = JSONObject()
                    jsonObject.put("latitude", lat)
                    jsonObject.put("longitude", lng)

                    if(globalClientType==10 || newMessage==""){
                        jsonObject.put("ticketid", 0)
                        jsonObject.put("routeid", "Out Data")
                    }
                    else{
                        jsonObject.put("ticketid", utilSharedPref.getValueInt("user_id").toString())
                        jsonObject.put("routeid", "1")
                    }

                    jsonObject.put("accesstoken", utilSharedPref.getValueString("user_token"))
                    jsonObject.put("message", inOutData)
                    jsonObject.put("date", getCurrentDateTime())
                    jsonObject.put("userid", utilSharedPref.getValueInt("user_id").toString())
                    jsonObject.put("beaconid",globalDeviceAddress)
                    jsonObject.put("tripid", "1077")
                    jsonObject.put("IsDriver", false)
                    jsonObject.put("IsBibo", true)
                    jsonObject.put("Clientid", utilSharedPref.getValueIntCOSI("zigClientID"))
                    val jsonArray=JSONArray()
                    jsonArray.put(jsonObject)
                    overallJsonObject.put("List",jsonArray)
                    myLog("limadata3",overallJsonObject.toString())


                    limaLogTest(context,""+overallJsonObject.toString(),utilSharedPref.getValueString("user_name").toString())


                    try{
                        val startTime = System.currentTimeMillis()
                        val uploadLatLng=getRootUrlV3() + "api/Beacon/Add"
                        val myBuilder: CronetEngine.Builder = CronetEngine.Builder(context)
                        myBuilder.enableHttpCache(CronetEngine.Builder.HTTP_CACHE_IN_MEMORY, 100 * 1024)
                        myBuilder.enableHttp2(true)
                        myBuilder.enableQuic(true)
                        val cronetEngine: CronetEngine = myBuilder.build()
                        val executor: Executor = Executors.newSingleThreadExecutor()
                        val requestBuilder: UrlRequest.Builder = cronetEngine.newUrlRequestBuilder(uploadLatLng,
                            OUTdataRequestCallback(context,startTime), executor
                        )
                        requestBuilder.setPriority(UrlRequest.Builder.REQUEST_PRIORITY_HIGHEST)
                        requestBuilder.setHttpMethod("POST")
                        requestBuilder.setUploadDataProvider(UploadDataProviders.create(overallJsonObject.toString().toByteArray()), executor)
                        requestBuilder.addHeader("Content-Type", "application/json; charset=utf-8")
                        val request: UrlRequest = requestBuilder.build()
                        request.start()
                    }
                    catch (e:Exception){
                        limaLogTest(context,"111"+ {e.message}, utilSharedPref.getValueString("user_name").toString())
                        myLog("limadata3", "beacon data catch 2 :: ${e.message}")
                    }



                } catch (e: JSONException) {
                    limaLogTest(context,"222"+e.toString(),utilSharedPref.getValueString("user_name").toString())
                    myLog("limadata3", "biboData() catch 1:"+e.message.toString())
                }
                catch (e: Exception) {
                    limaLogTest(context,"333"+e.toString(),utilSharedPref.getValueString("user_name").toString())
                    myLog("limadata3", "biboData() catch 2:"+e.message.toString())
                }
            }
        }
        catch (e:java.lang.Exception){
            myLog("limadata3", "biboData() catch 2:"+e.message.toString())
        }
    }
    else{
        myLog("limadata3", "biboData() no mac address: *")
    }


}







//...............................................................................................google log - bibo, payment failure
fun sendData(currentLocation: Location, mode: String, inBuildSpeed: String, Kmph: String, mph: String, context: Context){

}
@SuppressLint("InlinedApi")
fun sendBIBOConnectionDetails(context: Context, timeInterval: String, status: String){

}



fun limaLogTest(context: Context,request :String,username :String){
}


@SuppressLint("InlinedApi")
fun sendErrorLog(context: Context, message: String){

}
//...............................................................................................
//  * Returns true if this is a service running in this app.
fun isServiceRunning(serviceClass: Class<*>, applicationContext: Context): Boolean {
    val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    // Loop through the running services
    @Suppress("DEPRECATION")
    for (service in activityManager.getRunningServices(Integer.MAX_VALUE)) {
        if (serviceClass.name == service.service.className) {
            return true
        }
    }
    return false
}
fun stopSmartService(applicationContextNew: Context) {
    myLog("ble009", "service stopSmartService()")
    try {
        val serviceRunningStatus = isServiceRunning(SmartService::class.java, applicationContextNew)
        if (serviceRunningStatus) {
            val intent = Intent(applicationContextNew, SmartService::class.java)
            applicationContextNew.stopService(intent)
        }else{
            myLog("ble009", "service stopSmartService() already stopped")
        }
    } catch (e: Exception) {
        myLog("ble009", "service stopSmartService() catch ${e.message}")
    }
}
fun stopAndStartSmartService(applicationContextNew: Context,message:String?="init") {
    myLog("ble009","service stopAndStartSmartService() init $message")
    myLog("screenwake2","service stopAndStartSmartService() init $message")

    if(x4maasServiceEnable){
        x4maasServiceEnable = false
        myLog("ble009","service stopAndStartSmartService() came in 1")
        try{
            val serviceRunningStatus = isServiceRunning(SmartService::class.java, applicationContextNew)
            if (serviceRunningStatus) {
                try{
                    val stop = Intent(applicationContextNew, SmartService::class.java)
                    applicationContextNew.stopService(stop)
                }catch (e: Exception){}
            }
            //...............................................................
            //start service
            val serviceRunningStatusSmart = isServiceRunning(SmartService::class.java, applicationContextNew)
            if(!serviceRunningStatusSmart){
                try{
                    applicationContextNew.startService(Intent(applicationContextNew, SmartService::class.java))
                }catch (e06: Exception){}
            }
        }
        catch (e: Exception){
            myLog("ble009", "service stopAndStartSmartService() catch ${e.message}")
        }
    }


}
fun stopAndStartSmartServiceNew(applicationContextNew: Context,message:String?="init") {
    myLog("ble009","service stopAndStartSmartService() New init $message")
    myLog("screenwake2","service stopAndStartSmartService() New init $message")

    if(x4maasServiceEnable){
        x4maasServiceEnable = false
        myLog("ble009","service stopAndStartSmartService() came in 2")

        try{
            val serviceRunningStatus = isServiceRunning(SmartService::class.java, applicationContextNew)
            myLog("screenwake2","service running status $serviceRunningStatus")
            if (serviceRunningStatus) {
                myLog("screenwake2","service running status if - $serviceRunningStatus")
                /* try{
                     val stop = Intent(applicationContextNew, SmartService::class.java)
                     applicationContextNew.stopService(stop)
                 }catch (e: Exception){}*/
            }
            else{
                myLog("screenwake2","service running status else - $serviceRunningStatus")

                //...............................................................
                //start service
                val serviceRunningStatusSmart = isServiceRunning(SmartService::class.java, applicationContextNew)
                if(!serviceRunningStatusSmart){
                    try{
                        applicationContextNew.startService(Intent(applicationContextNew, SmartService::class.java))
                    }catch (e06: Exception){}
                }
            }

        }
        catch (e: Exception){
            myLog("ble009", "service stopAndStartSmartService() catch ${e.message}")
        }
    }


}
//...............................................................................................
// * find battery percentage
fun getBatteryPercentage(context: Context): String {
    return try{
        val bm = context.getSystemService(BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toString()
    }catch (e:Exception){
        "catch : ${e.message}"
    }
}
// * find notification is enabled or not
fun areNotificationsEnabled(context: Context, channelId: String = "Zig "): String {
    try{
        // check if global notification switch is ON or OFF
        if (NotificationManagerCompat.from(context).areNotificationsEnabled())
        // if its ON then we need to check for individual channels in OREO
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = manager.getNotificationChannel(context.getString(R.string.app_name))
                return (channel?.importance != NotificationManager.IMPORTANCE_NONE).toString()
            } else {
                // if this less then OREO it means that notifications are enabled
                "no need"
            }
        // if this is less then OREO it means that notifications are disabled
        return "false"
    }catch (e:Exception){
        return "catch : ${e.message}"
    }
}
// * find current time
fun getCurrentDateTime():String{
    var currentDateAndTime              = ""
    val currentDateFormat               = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
    currentDateFormat.timeZone          = TimeZone.getTimeZone("America/New_York")
    currentDateAndTime                  = currentDateFormat.format(Date())
    return currentDateAndTime
}
fun getCurrentSysDateTime():String{
    var currentDateAndTime              = ""
    val currentDateFormat               = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
    currentDateFormat.timeZone          = TimeZone.getDefault()
    currentDateAndTime                  = currentDateFormat.format(Date())
    return currentDateAndTime
}
fun getCurrentDateTimeZed():String{
    var currentDateAndTime              = ""
    val currentDateFormat               = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
    currentDateFormat.timeZone          = TimeZone.getTimeZone("America/New_York")
    currentDateAndTime                  = currentDateFormat.format(Date())
    return currentDateAndTime
}
fun getCurrentDateTimeNew():String{
    var currentDateAndTime              = ""
    val currentDateFormat               = SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault())
    currentDateFormat.timeZone          = TimeZone.getTimeZone("America/New_York")
    currentDateAndTime                  = currentDateFormat.format(Date())
    return currentDateAndTime
}

fun getCurrentDateTimeForBeacon():String{ //2023-02-14 20:18:57
    var currentDateAndTime              = ""
    val currentDateFormat               = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    currentDateFormat.timeZone          = TimeZone.getTimeZone("America/New_York")
    currentDateAndTime                  = currentDateFormat.format(Date())
    return currentDateAndTime
}
fun calculateDate3(dateStart: String, dateStop: String): String {
    val format = SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault())
    var d1: Date? = null
    var d2: Date? = null
    return try {
        d1 = format.parse(dateStart)
        d2 = format.parse(dateStop)
        //in milliseconds
        val diffSeconds = (d2.time - d1.time) / 1000
        diffSeconds.toString()
    }catch (e:Exception){
        "0"
    }
}



