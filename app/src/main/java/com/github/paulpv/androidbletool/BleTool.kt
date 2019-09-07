package com.github.paulpv.androidbletool

import android.Manifest
import android.app.*
import android.content.*
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
class BleTool(private var applicationContext: Context) {

    companion object {
        private const val TAG = "BleTool"

        private const val AUTO_START_SCANNING = false

        private const val SCAN_REQUEST_CODE = 69

        private const val SCANNING_NOTIFICATION_ID = 1
        private const val SCANNING_CHANNEL_ID = "SCANNING_CHANNEL_ID"
        private const val SCANNING_CHANNEL_NAME = "SCANNING_CHANNEL_NAME"
    }

    class BleToolApp : Application() {
        lateinit var bleTool: BleTool
            private set

        override fun onCreate() {
            super.onCreate()
            bleTool = BleTool(this)
        }
    }

    class BleScanReceiver : BroadcastReceiver() {
        companion object {
            fun newPendingIntent(context: Context, requestCode: Int): PendingIntent =
                Intent(context, BleScanReceiver::class.java).let {
                    PendingIntent.getBroadcast(context, requestCode, it, 0)
                }
        }

        override fun onReceive(context: Context, intent: Intent) {
            (context.applicationContext as BleToolApp?)?.bleTool!!.onScanResultReceived(intent)
        }
    }

    interface BleToolObserver : Observer<ScanResult>

    abstract class BleToolObserverActivity : BleToolObserver, AppCompatActivity()

    private var notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var foregroundService: NotificationService? = null
    private lateinit var foregroundNotification: Notification
    private val serviceConnection: ServiceConnection

    private val rxBleClient: RxBleClient = RxBleClient.create(applicationContext)
    private val callbackIntent = BleScanReceiver.newPendingIntent(applicationContext, SCAN_REQUEST_CODE)

    private var scanResultObservers: MutableSet<BleToolObserver> = mutableSetOf()

    private var nonBackgroundScanDisposable: Disposable? = null
    private var rxBleClientStateChangeObservable: Observable<RxBleClient.State>? = null

    private var bluetoothAdapter = BluetoothUtils.getBluetoothAdapter(applicationContext)

    var isScanning = false
        private set

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
                foregroundService = (service as NotificationService.NotificationBinder).getServiceInstance()
                @Suppress("ConstantConditionIf")
                if (AUTO_START_SCANNING) {
                    isScanning = scanInternal(true)
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                foregroundService = null
                showScanningNotification(show = false)
            }
        }

        val service = Intent(applicationContext, NotificationService::class.java)
        applicationContext.bindService(service, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun shutdown(runThenKillProcess: (() -> Unit)? = null) {
        scanInternal(false)
        isScanning = false

        //rxBleClientStateChangeObservable?.dispose()
        rxBleClientStateChangeObservable = null

        applicationContext.unbindService(serviceConnection)

        if (runThenKillProcess != null) {
            runThenKillProcess.invoke()
            exitProcess(1)
        }
    }

    private fun updateScanningNotificationText(text: String) {
        val notification = createNotification(applicationContext, SCANNING_CHANNEL_ID, text)
        notificationManager.notify(SCANNING_NOTIFICATION_ID, notification)
    }

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

    private fun getScanningNotificationText(bluetoothAdapterEnabled: Boolean): String {
        val resId = if (bluetoothAdapterEnabled) R.string.scanning else R.string.waiting_for_bluetooth
        return getString(resId)
    }

    private fun showScanningNotification(
        bluetoothAdapterEnabled: Boolean = isBluetoothEnabled,
        show: Boolean
    ) {
        if (show) {
            val text = getScanningNotificationText(bluetoothAdapterEnabled)
            foregroundNotification = createNotification(foregroundService!!, SCANNING_CHANNEL_ID, text)
            //
            // Prevent Android OS from suspending this app's process
            //
            foregroundService?.startForeground(SCANNING_NOTIFICATION_ID, foregroundNotification)
        } else {
            notificationManager.cancel(SCANNING_NOTIFICATION_ID)
            //
            // Allow Android OS to suspend this app's process
            //
            foregroundService?.stopForeground(true)
        }
    }

    /**
     * Called by UI Contexts [Activity/Fragment] to start scanning
     */
    fun scanAttach(observerActivity: BleToolObserverActivity) {
        if (scanResultObservers.contains(observerActivity)) {
            return
        }
        observerActivity.runWithPermissions(Manifest.permission.ACCESS_COARSE_LOCATION) {
            scanAttach(observerActivity as BleToolObserver)
        }
    }

    /**
     * Called by non-UI Contexts to start scanning
     */
    fun scanAttach(observer: BleToolObserver) {
        if (scanResultObservers.add(observer) && scanResultObservers.size == 1) {
            if (scanInternal(true)) {
                isScanning = true
            }
        }
    }

    /**
     * Called by either UI or non-UI Contexts to stop scanning
     */
    fun scanDetach(observer: BleToolObserver) {
        if (scanResultObservers.remove(observer) && scanResultObservers.size == 0) {
            if (scanInternal(false)) {
                isScanning = false
            }
        }
    }

    private fun scanInternal(on: Boolean): Boolean {
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
                    Log.e(TAG, "scanInternal: USE_API_VERSION >= 26; Start background scan")
                    rxBleClient.backgroundScanner.scanBleDeviceInBackground(
                        callbackIntent,
                        scanSettings,
                        scanFilter
                    )
                } else {
                    Log.e(TAG, "scanInternal: USE_API_VERSION < 26; Start non-background scan")
                    nonBackgroundScanDisposable = rxBleClient.scanBleDevices(scanSettings, scanFilter)
                        .observeOn(AndroidSchedulers.mainThread())
                        //.doFinally { dispose() }
                        .subscribe({ processScanResult(it) }, { onScanError(it) })
                    //.let { scanDisposable = it }
                    showScanningNotification(isBluetoothEnabled, true)
                }
                return true
            } catch (scanException: BleScanException) {
                Log.e(TAG, "scanInternal: Failed to start scan", scanException)
                onScanError(scanException)
            }
        } else {
            try {
                if (USE_SCAN_API_VERSION >= 26) {
                    Log.e(TAG, "scanInternal: USE_API_VERSION >= 26; Stopping background scan")
                    rxBleClient.backgroundScanner.stopBackgroundBleScan(callbackIntent)
                } else {
                    Log.e(TAG, "scanInternal: USE_API_VERSION < 26; Stopping non-background scan")
                    nonBackgroundScanDisposable?.dispose()
                    nonBackgroundScanDisposable = null
                    showScanningNotification(show = false)
                }
                return true
            } catch (scanException: BleScanException) {
                Log.e(TAG, "scanInternal: Failed to stop scan", scanException)
                onScanError(scanException)
            }
        }
        return false
    }

    fun onScanResultReceived(intent: Intent) {
        try {
            val scanResult = rxBleClient.backgroundScanner.onScanResultReceived(intent)
            //Log.i(TAG, "Scan results received: $scanResults")
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
        scanResultObservers.forEach { it.onNext(scanResult) }
    }


}