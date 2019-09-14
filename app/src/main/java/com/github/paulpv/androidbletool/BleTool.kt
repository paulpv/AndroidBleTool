package com.github.paulpv.androidbletool

import android.Manifest
import android.app.*
import android.content.*
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.livinglifetechway.quickpermissions_kotlin.runWithPermissions
import com.polidea.rxandroidble2.LogConstants
import com.polidea.rxandroidble2.LogOptions
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.exceptions.BleScanException
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanResult
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlin.system.exitProcess

/**
 * To call from Java:
 * https://kotlinlang.org/docs/reference/java-to-kotlin-interop.html
 */
class BleTool(private var applicationContext: Context, looper: Looper = Looper.getMainLooper()) {

    class BleToolApp : Application() {
        companion object {
            private const val TAG = "BleToolApp"
        }

        lateinit var bleTool: BleTool
            private set

        override fun onCreate() {
            Log.i(TAG, "onCreate()")
            super.onCreate()
            bleTool = BleTool(this)
        }

        override fun onTerminate() {
            Log.i(TAG, "onTerminate()")
            super.onTerminate()
        }
    }

    class BleScanResult(val bleTool: BleTool, val scanResult: ScanResult)

    interface BleToolObserver : Observer<BleScanResult>

    abstract class BleToolObserverActivity : BleToolObserver, AppCompatActivity()

    //
    //
    //

    companion object {
        private const val TAG = "BleTool"

        private const val AUTO_START_SCANNING = false

        private const val SCAN_REQUEST_CODE = 69

        private const val SCANNING_NOTIFICATION_ID = 1
        private const val SCANNING_CHANNEL_ID = "SCANNING_CHANNEL_ID"
        private const val SCANNING_CHANNEL_NAME = "SCANNING_CHANNEL_NAME"

        private val MY_PID = android.os.Process.myPid()
    }

    class BleDeviceScanReceiver : BroadcastReceiver() {
        companion object {
            private const val ACTION = "com.github.paulpv.androidbletool.BleTool.BleDeviceScanReceiver.ACTION"

            fun newPendingIntent(context: Context, requestCode: Int): PendingIntent {
                val intent = Intent(context, BleDeviceScanReceiver::class.java)
                intent.action = ACTION
                return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            }
        }

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION -> {
                    (context.applicationContext as BleToolApp?)?.bleTool!!.onScanResultReceived(context, intent)
                }
            }
        }
    }

    /**
     * Requires android.permission.RECEIVE_BOOT_COMPLETED
     * <p>
     * Test on emulator:
     * 1) adb kill-server
     * 2) adb root
     * 3) adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -n com.github.paulpv.androidbletool/com.github.paulpv.androidbletool.BleTool.BootCompletedReceiver
     */
    class BootCompletedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                    (context.applicationContext as BleToolApp?)?.bleTool!!.onBootCompletedReceived(context, intent)
                }
            }
        }
    }

    class NotificationService : Service() {
        override fun onBind(intent: Intent?): IBinder {
            return NotificationBinder()
        }

        inner class NotificationBinder : Binder() {
            fun getServiceInstance(): NotificationService = this@NotificationService
        }
    }

    private val PREFS_FILENAME = "com.github.paulpv.androidbletool.BleTool.prefs"
    private val sharedPreferences = applicationContext.getSharedPreferences(PREFS_FILENAME, 0)

    private var notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var foregroundService: NotificationService? = null
    private lateinit var foregroundNotification: Notification
    private val serviceConnection: ServiceConnection

    private var bluetoothAdapter = BluetoothUtils.getBluetoothAdapter(applicationContext)

    private val rxBleClient: RxBleClient = RxBleClient.create(applicationContext)
    private val callbackIntent = BleDeviceScanReceiver.newPendingIntent(applicationContext, SCAN_REQUEST_CODE)

    private var scanResultObservers: MutableSet<BleToolObserver> = mutableSetOf()

    private var nonBackgroundScanDisposable: Disposable? = null
    private var rxBleClientStateChangeObservable: Observable<RxBleClient.State>? = null

    private val RECENT_DEVICE_TIMEOUT_MILLIS = 30 * 1000
    private val recentlyNearbyDevices: ExpiringIterableLongSparseArray<ScanResult> =
        ExpiringIterableLongSparseArray("recentlyNearbyDevices", RECENT_DEVICE_TIMEOUT_MILLIS, looper)
    private val recentlyNearbyDevicesListener = object : ExpiringIterableLongSparseArray.ExpiringIterableLongSparseArrayListener<ScanResult> {
        override fun onItemAdded(key: Long, index: Int, value: ScanResult) {
            //Log.i(TAG, "onItemAdded: key=$key, index=$index, value=$value")
            onDeviceAdded(value)
        }

        override fun onItemExpiring(key: Long, index: Int, value: ScanResult, elapsedMillis: Long): Boolean {
            //Log.w(TAG, "onItemExpiring: key=$key, index=$index, value=$value, elapsedMillis=$elapsedMillis")
            return onDeviceExpiring(value)
        }

        override fun onItemRemoved(key: Long, index: Int, value: ScanResult, elapsedMillis: Long, expired: Boolean) {
            //Log.i(TAG, "onItemRemoved: key=$key, index=$index, value=$value, elapsedMillis=$elapsedMillis, expired=$expired")
            onDeviceRemoved(value)
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    var scanningStartedMillis: Long
        get() {
            return sharedPreferences!!.getLong("scanningStartedMillis", -1)
        }
        private set(value) {
            sharedPreferences!!.edit(commit = true, action = {
                putLong("scanningStartedMillis", value)
            })
        }

    val scanningElapsedMillis: Long
        get() {
            val scanningStartedMillis = this.scanningStartedMillis
            return if (scanningStartedMillis != -1L) {
                SystemClock.uptimeMillis() - scanningStartedMillis
            } else {
                -1L
            }
        }

    @Suppress("MemberVisibilityCanBePrivate")
    val isScanning: Boolean
        get() {
            return scanningStartedMillis != -1L
        }

    private fun getString(resId: Int, vararg formatArgs: Any): String {
        return applicationContext.getString(resId, formatArgs)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    val isBluetoothEnabled: Boolean
        get() = BluetoothUtils.isBluetoothAdapterEnabled(bluetoothAdapter)

    @Suppress("PrivatePropertyName")
    private var _USE_SCAN_API_VERSION = Build.VERSION.SDK_INT
    @Suppress("PropertyName")
    var USE_SCAN_API_VERSION: Int
        get() = _USE_SCAN_API_VERSION
        set(value) {
            if (isScanning) {
                scanInternal(false)
            }
            _USE_SCAN_API_VERSION = value
            if (isScanning) {
                scanInternal(true)
            }
        }


    init {
        Log.i(TAG, "+init")

        @Suppress("SimplifyBooleanWithConstants")
        if (false && BuildConfig.DEBUG) {
            USE_SCAN_API_VERSION = 25 // For debugging purposes only
        }

        @Suppress("SimplifyBooleanWithConstants")
        if (false && BuildConfig.DEBUG) {
            RxBleClient.updateLogOptions(
                LogOptions.Builder()
                    .setLogLevel(LogConstants.INFO)
                    .setMacAddressLogSetting(LogConstants.MAC_ADDRESS_FULL)
                    .setUuidsLogSetting(LogConstants.UUIDS_FULL)
                    .setShouldLogAttributeValues(true)
                    .build()
            )
        }

        recentlyNearbyDevices.addListener(recentlyNearbyDevicesListener)

        rxBleClientStateChangeObservable = rxBleClient.observeStateChanges()
            .startWith(rxBleClient.state)
            .switchMap {
                when (it) {
                    RxBleClient.State.READY -> {
                        if (isScanning) {
                            scanInternal(true)
                        }
                    }
                    RxBleClient.State.BLUETOOTH_NOT_AVAILABLE,
                    RxBleClient.State.BLUETOOTH_NOT_ENABLED,
                    RxBleClient.State.LOCATION_PERMISSION_NOT_GRANTED,
                    RxBleClient.State.LOCATION_SERVICES_NOT_ENABLED ->
                        if (isScanning) {
                            scanInternal(false)
                        }
                    else -> {
                        Log.w(TAG, "Unhandled RxBleClient.state=$it")
                    }
                }
                Observable.just(it)
            }

        if (Build.VERSION.SDK_INT >= 26) {
            createNotificationChannel(
                notificationManager,
                SCANNING_CHANNEL_ID,
                SCANNING_CHANNEL_NAME,
                NotificationManagerCompat.IMPORTANCE_LOW,
                getString(R.string.notification_scanning_channel_description)
            )
        }

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val notificationService = (service as NotificationService.NotificationBinder).getServiceInstance()
                onNotificationServiceConnected(notificationService)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                onNotificationServiceDisconnected()
            }
        }

        val service = Intent(applicationContext, NotificationService::class.java)
        applicationContext.bindService(service, serviceConnection, Context.BIND_AUTO_CREATE)

        Log.i(TAG, "-init")
    }

    private fun shutdown(runThenKillProcess: (() -> Unit)? = null) {
        scanInternal(false)

        //rxBleClientStateChangeObservable?.dispose()
        rxBleClientStateChangeObservable = null

        applicationContext.unbindService(serviceConnection)

        if (runThenKillProcess != null) {
            runThenKillProcess.invoke()
            exitProcess(1)
        }
    }

    private fun onNotificationServiceConnected(notificationService: NotificationService) {
        Log.i(TAG, "onServiceConnected(...)")
        foregroundService = notificationService
        if (isScanning) {
            scanInternal(true)
        }
    }

    private fun onNotificationServiceDisconnected() {
        Log.i(TAG, "onServiceDisconnected(...)")
        scanInternal(false)
        foregroundService = null
    }

    //
    //
    //

    private fun onBootCompletedReceived(context: Context, intent: Intent) {
        Log.i(TAG, "#BOOT onBootCompletedReceived(context=$context, intent=$intent)")
        Log.i(TAG, "#BOOT onBootCompletedReceived: isScanning=$isScanning")
        // Scanning will be started in onNotificationServiceConnected if needed
    }

    private fun onAppReplacedReceived(context: Context, intent: Intent) {
        Log.i(TAG, "#REPLACED onAppReplacedReceived(context=$context, intent=$intent)")
        Log.i(TAG, "#REPLACED onAppReplacedReceived: isScanning=$isScanning")
        // Scanning will be started in onNotificationServiceConnected if needed
    }

    //
    //
    //

    @RequiresApi(26)
    private fun createNotificationChannel(
        notificationManager: NotificationManager,
        channelID: String,
        channelName: String,
        channelImportance: Int,
        channelDescription: String
    ) {
        val channel = NotificationChannel(channelID, channelName, channelImportance)
        channel.description = channelDescription
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(context: Context, channelID: String, text: String): Notification =
        NotificationCompat.Builder(context, channelID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setShowWhen(false)
            .setChannelId(channelID)
            .build()

    private fun updateScanningNotificationText(text: String) {
        val notification = createNotification(applicationContext, SCANNING_CHANNEL_ID, text)
        notificationManager.notify(SCANNING_NOTIFICATION_ID, notification)
    }

    private fun getScanningNotificationText(): String {
        val resId = if (isBluetoothEnabled) R.string.scanning else R.string.waiting_for_bluetooth
        return getString(resId)
    }

    private fun showScanningNotification(show: Boolean) {
        if (foregroundService == null) {
            Log.w(TAG, "showScanningNotification: Unexpected foregroundService == null; ignoring")
            return
        }
        if (show) {
            val text = getScanningNotificationText()
            foregroundNotification = createNotification(foregroundService!!, SCANNING_CHANNEL_ID, text)
            //
            // Prevent Android OS from suspending this app's process
            //
            foregroundService!!.startForeground(SCANNING_NOTIFICATION_ID, foregroundNotification)
        } else {
            notificationManager.cancel(SCANNING_NOTIFICATION_ID)
            //
            // Allow Android OS to suspend this app's process
            //
            foregroundService!!.stopForeground(true)
        }
    }

    //
    //
    //

    fun attach(observer: BleToolObserver) {
        scanResultObservers.add(observer)
    }

    fun detach(observer: BleToolObserver) {
        scanResultObservers.remove(observer)
    }

    fun scan(on: Boolean, observerActivity: BleToolObserverActivity, force: Boolean = false) {
        observerActivity.runWithPermissions(Manifest.permission.ACCESS_COARSE_LOCATION) {
            scan(on, force)
        }
    }

    fun scan(on: Boolean, force: Boolean = false) {
        if (force || on != isScanning) {
            scanInternal(on)
        } else {
            Log.w(TAG, "scan(on=$on); on == isScanning; ignoring")
        }
    }

    private fun scanInternal(on: Boolean): Boolean {
        Log.i(TAG, "scanInternal(on=$on)")
        if (on) {
            try {
                val scanSettings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .build()

                val scanFilter = ScanFilter.Builder()
                    // add custom filters if needed
                    //...
                    .build()

                if (USE_SCAN_API_VERSION >= 26) {
                    Log.i(TAG, "scanInternal: USE_API_VERSION >= 26; Start background scan")
                    rxBleClient.backgroundScanner.scanBleDeviceInBackground(
                        callbackIntent,
                        scanSettings,
                        scanFilter
                    )
                    backgroundScannerStartedPid = MY_PID
                } else {
                    Log.i(TAG, "scanInternal: USE_API_VERSION < 26; Start non-background scan")
                    nonBackgroundScanDisposable = rxBleClient.scanBleDevices(scanSettings, scanFilter)
                        .observeOn(AndroidSchedulers.mainThread())
                        //.doFinally { dispose() }
                        .subscribe({ processScanResult(it) }, { onScanError(it) })
                    //.let { scanDisposable = it }
                }
                showScanningNotification(true)
                scanningStartedMillis = SystemClock.uptimeMillis()
                return true
            } catch (scanException: BleScanException) {
                Log.e(TAG, "scanInternal: Failed to start scan", scanException)
                onScanError(scanException)
            }
        } else {
            try {
                if (USE_SCAN_API_VERSION >= 26) {
                    Log.i(TAG, "scanInternal: USE_API_VERSION >= 26; Stopping background scan")
                    rxBleClient.backgroundScanner.stopBackgroundBleScan(callbackIntent)
                    backgroundScannerStartedPid = -1
                } else {
                    Log.i(TAG, "scanInternal: USE_API_VERSION < 26; Stopping non-background scan")
                    nonBackgroundScanDisposable?.dispose()
                    nonBackgroundScanDisposable = null
                }
                showScanningNotification(false)
                recentlyNearbyDevices.pause()
                scanningStartedMillis = -1L
                return true
            } catch (scanException: BleScanException) {
                Log.e(TAG, "scanInternal: Failed to stop scan", scanException)
                onScanError(scanException)
            }
        }
        scanningStartedMillis = -1L
        return false
    }

    private var backgroundScannerStartedPid: Int
        get() {
            @Suppress("UnnecessaryVariable")
            val value = sharedPreferences!!.getInt("backgroundScannerStartedPid", -1)
            //Log.e(TAG, "#PID get backgroundScannerStartedPid=$value")
            return value
        }
        set(value) {
            sharedPreferences!!.edit(commit = true, action = {
                //Log.e(TAG, "#PID set backgroundScannerStartedPid=$value")
                putInt("backgroundScannerStartedPid", value)
            })
        }

    fun onScanResultReceived(context: Context, intent: Intent) {
        val backgroundScannerStartedPid = backgroundScannerStartedPid
        if (backgroundScannerStartedPid == -1) {
            if (false && BuildConfig.DEBUG) {
                Log.w(
                    TAG,
                    "#PID onScanResultReceived: backgroundScannerStartedPid==-1; ignoring"
                )
            }
            return
        }

        if (backgroundScannerStartedPid != MY_PID) {
            if (true && BuildConfig.DEBUG) {
                Log.w(
                    TAG,
                    "#PID onScanResultReceived: backgroundScannerStartedPid($backgroundScannerStartedPid) != android.os.Process.myPid()($MY_PID); Scan is orphaned/leaked and cannot be stopped! :("
                )
            }
        } else {
            if (false && BuildConfig.DEBUG) {
                Log.i(
                    TAG,
                    "#PID onScanResultReceived: backgroundScannerStartedPid($backgroundScannerStartedPid) == android.os.Process.myPid()($MY_PID); Scan is **NOT** orphaned/leaked and can be stopped! :)"
                )
            }
        }

        try {
            val scanResult = rxBleClient.backgroundScanner.onScanResultReceived(intent)
            //Log.i(TAG, "onScanResultReceived: Scan results received: $scanResults")
            scanResult.forEach { processScanResult(it) }
        } catch (scanException: BleScanException) {
            Log.e(TAG, "Failed to scan devices", scanException)
            onScanError(scanException)
        }
    }

    private fun onScanError(throwable: Throwable) {
        scanResultObservers.forEach { it.onError(throwable) }
    }

    private fun processScanResult(scanResult: ScanResult) {
        //Log.i(TAG, "processScanResult: scanResult=$scanResult")
        // TODO:(pv) Save individual results in to expirable collection
        // TODO:(pv) Emit each add and remove (via timeout)
        val bleDevice = scanResult.bleDevice
        val macAddressString = bleDevice.macAddress
        val macAddressLong = BluetoothUtils.macAddressStringToLong(macAddressString)
        val index = recentlyNearbyDevices.put(macAddressLong, scanResult)
        if (index >= 0) {
            onDeviceUpdated(scanResult)
        }
    }

    private fun onDeviceAdded(scanResult: ScanResult) {
        val bleDevice = scanResult.bleDevice
        val macAddressString = bleDevice.macAddress
        Log.i(
            TAG,
            "scanningElapsedMillis=${Utils.getTimeDurationFormattedString(scanningElapsedMillis!!)} $macAddressString onDeviceAdded: ADDED!"
        )
    }

    private fun onDeviceUpdated(scanResult: ScanResult) {
        val bleDevice = scanResult.bleDevice
        val macAddressString = bleDevice.macAddress
        Log.v(
            TAG,
            "scanningElapsedMillis=${Utils.getTimeDurationFormattedString(scanningElapsedMillis!!)} $macAddressString onDeviceUpdated: SCANNED!"
        )
    }

    private fun onDeviceExpiring(scanResult: ScanResult): Boolean {
        val bleDevice = scanResult.bleDevice
        val macAddressString = bleDevice.macAddress
        //Log.w(TAG, "scanningElapsedMillis=${Utils.getTimeDurationFormattedString(scanningElapsedMillis!!)} $macAddressString onDeviceExpiring: EXPIRING...")
        //Log.w(TAG, "scanningElapsedMillis=${Utils.getTimeDurationFormattedString(scanningElapsedMillis!!)} $macAddressString onDeviceExpiring: isScanning=$isScanning")
        @Suppress("UnnecessaryVariable")
        val keep = !isScanning || scanningElapsedMillis < RECENT_DEVICE_TIMEOUT_MILLIS
        //Log.w(TAG, "scanningElapsedMillis=${Utils.getTimeDurationFormattedString(scanningElapsedMillis!!)} $macAddressString onDeviceExpiring: keep=$keep")
        return keep
    }

    private fun onDeviceRemoved(scanResult: ScanResult) {
        val bleDevice = scanResult.bleDevice
        val macAddressString = bleDevice.macAddress
        Log.i(
            TAG,
            "${Utils.getTimeDurationFormattedString(scanningElapsedMillis!!)} $macAddressString onDeviceRemoved: REMOVED!"
        )
    }
}