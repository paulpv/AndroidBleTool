package com.github.paulpv.androidbletool

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.*
import android.content.*
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.github.paulpv.androidbletool.BluetoothUtils.callbackTypeToString
import com.github.paulpv.androidbletool.collections.ExpiringIterableLongSparseArray
import com.github.paulpv.androidbletool.exceptions.BleScanException
import com.github.paulpv.androidbletool.gatt.GattManager
import com.github.paulpv.androidbletool.utils.ReflectionUtils
import com.github.paulpv.androidbletool.utils.Utils
import com.github.paulpv.androidbletool.utils.Utils.TAG
import com.livinglifetechway.quickpermissions_kotlin.runWithPermissions
import com.livinglifetechway.quickpermissions_kotlin.util.PermissionsUtil
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.system.exitProcess

/**
 * To call from Java:
 * https://kotlinlang.org/docs/reference/java-to-kotlin-interop.html
 */
class BleTool(
    private val application: Application,
    private val configuration: BleToolConfiguration,
    private val deviceScanTimeoutMillis: Int = DEVICE_SCAN_TIMEOUT_MILLIS_DEFAULT,
    looper: Looper? = null
) {
    companion object {
        private val TAG = TAG(BleTool::class.java)

        val DEVICE_SCAN_TIMEOUT_MILLIS_DEFAULT: Int = if (BuildConfig.DEBUG) {
            33 * 1000 // 33 seconds == 3300 milliseconds
        } else {
            (5.5 * 60).toInt() * 1000 // 5.5 minutes == 330000 milliseconds
        }

        @Suppress("SimplifyBooleanWithConstants")
        private val DEBUG_FORCE_PERSISTENT_SCANNING_RESET = false && BuildConfig.DEBUG

        private const val SCANNING_NOTIFICATION_REQUEST_CODE = 42
        private const val SCANNING_NOTIFICATION_ID = 1
        private const val SCANNING_NOTIFICATION_CHANNEL_ID = "SCANNING_NOTIFICATION_CHANNEL_ID"
        private const val SCANNING_NOTIFICATION_CHANNEL_NAME = "SCANNING_NOTIFICATION_CHANNEL_NAME"

        private const val SCAN_RECEIVER_REQUEST_CODE = 69

        private val MY_PID = Process.myPid()

        fun getInstance(context: Context): BleTool? {
            return (context.applicationContext as? BleToolApplication)?.bleTool
        }

        @Suppress("PropertyName")
        val SCAN_FILTER_EMPTY: ScanFilter = ScanFilter.Builder().build()
    }

    interface BleToolApplication {
        val bleTool: BleTool
    }

    interface BleToolScanningNotificationInfo {
        /**
         * May be calculated dynamically
         */
        val activityClass: Class<out Activity>

        val channelDescription: String

        fun getSmallIcon(isForegrounded: Boolean): Int

        val contentTitle: String

        fun getContentText(isBluetoothEnabled: Boolean): String
    }

    interface BleToolConfiguration {
        val scanningNotificationInfo: BleToolScanningNotificationInfo

        @Suppress("PropertyName")
        val SCAN_FILTERS: List<ScanFilter>

        @Suppress("PropertyName")
        val DEBUG_DEVICE_ADDRESS_FILTER: Set<String>?
    }

    //
    //region DEBUG_DEVICE_ADDRESS_FILTER
    //
    private val DEBUG_DEVICE_ADDRESS_FILTER = configuration.DEBUG_DEVICE_ADDRESS_FILTER

    private fun debugDeviceAddressFilterPass(deviceMacAddress: String): Boolean {
        if (DEBUG_DEVICE_ADDRESS_FILTER != null && DEBUG_DEVICE_ADDRESS_FILTER.isNotEmpty()) {
            if (!DEBUG_DEVICE_ADDRESS_FILTER.contains(deviceMacAddress)) {
                return false
            }
        }
        return true
    }

    private fun debugDeviceAddressFilterPass(device: BluetoothDevice): Boolean {
        return debugDeviceAddressFilterPass(device.address)
    }
    //
    //endregion DEBUG_DEVICE_ADDRESS_FILTER
    //

    interface DeviceScanObserver {
        fun onDeviceScanError(bleTool: BleTool, e: Throwable): Boolean
        fun onDeviceAdded(bleTool: BleTool, item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>)
        fun onDeviceUpdated(bleTool: BleTool, item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>)
        fun onDeviceRemoved(bleTool: BleTool, item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>)
    }

    //
    //
    //

    /**
     * TL;DR: As of Android 24 (7/Nougat) [android.bluetooth.le.BluetoothLeScanner.startScan] is limited to 5 calls in 30 seconds.
     * 30 seconds / 5 = average 6 seconds between calls
     * 50% duty cycle = minimum 3 seconds on, 3 seconds off, 3 seconds on, 3 seconds off, repeat...
     * Add 100 milliseconds just to be safe
     *
     * https://blog.classycode.com/undocumented-android-7-ble-behavior-changes-d1a9bd87d983
     * The OS/API will *NOT* generate any errors.
     * You will only see "GattService: App 'yxz' is scanning too frequently" as a logcat error
     *
     * <p>
     * https://android.googlesource.com/platform/packages/apps/Bluetooth/+/master/src/com/android/bluetooth/gatt/GattService.java#1896
     * <p>
     * https://android.googlesource.com/platform/packages/apps/Bluetooth/+/master/src/com/android/bluetooth/gatt/AppScanStats.java#286
     * <p>
     * NUM_SCAN_DURATIONS_KEPT = 5
     * https://android.googlesource.com/platform/packages/apps/Bluetooth/+/master/src/com/android/bluetooth/gatt/AppScanStats.java#82
     * EXCESSIVE_SCANNING_PERIOD_MS = 30 seconds
     * https://android.googlesource.com/platform/packages/apps/Bluetooth/+/master/src/com/android/bluetooth/gatt/AppScanStats.java#88
     * <p>
     * In summary:
     * "The change prevents an app from stopping and starting BLE scans more than 5 times in a window of 30 seconds.
     * While this sounds like a completely innocuous and sensible change, it still caught us by surprise. One reason we
     * missed it is that the app is not informed of this condition. The scan will start without an error, but the
     * Bluetooth stack will simply withhold advertisements for 30 seconds, instead of informing the app through the
     * error callback ScanCallback.onScanFailed(int)."
     * 5 scans within 30 seconds == 6 seconds per scan.
     * We want 50% duty cycle, so 3 seconds on, 3 seconds off.
     * Increase this number a tiny bit so that we don't get too close to accidentally set off the scan timeout logic.
     * <p>
     * See also:
     * https://github.com/AltBeacon/android-beacon-library/issues/554
     */
    @Suppress("MemberVisibilityCanBePrivate")
    class AndroidBleScanStartLimits {
        companion object {
            const val scanStartLimitCount = 5
            const val scanStartLimitSeconds = 30
            const val scanStartLimitAverageSecondsPerCall = scanStartLimitSeconds / scanStartLimitCount.toFloat()
            const val scanIntervalDutyCycle = 0.5
            val scanStartIntervalAverageMinimumMillis = ceil(scanStartLimitAverageSecondsPerCall * scanIntervalDutyCycle * 1000).toLong()
            val scanStartIntervalAverageSafeMillis = scanStartIntervalAverageMinimumMillis + 100
        }
    }

    /**
     * Implicit Broadcast Exceptions registered in AndroidManifest.xml
     * https://developer.android.com/guide/components/broadcast-exceptions.html
     * Intent.ACTION_BOOT_COMPLETED
     * Intent.ACTION_LOCKED_BOOT_COMPLETED
     *
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
                    getInstance(context)?.onAppProcessStartReceived(context, intent)
                }
            }
        }
    }

    private abstract class ExplicitBroadcastReceiver(TAG: String, private val context: Context) : BroadcastReceiver() {

        @Suppress("PrivatePropertyName")
        private val TAG = TAG(TAG)

        abstract val intentFilter: IntentFilter

        private var isRegistered = false

        fun register() {
            Log.i(TAG, "+register()")
            if (!isRegistered) {
                Log.v(TAG, "register: context.registerReceiver(this, intentFilter)")
                isRegistered = context.registerReceiver(this, intentFilter) != null
            }
            Log.i(TAG, "-register()")
        }

        fun unregister() {
            Log.i(TAG, "+unregister()")
            if (isRegistered) {
                Log.v(TAG, "register: context.unregisterReceiver(this)")
                context.unregisterReceiver(this)
                isRegistered = false
            }
            Log.i(TAG, "-unregister()")
        }

        override fun onReceive(context: Context, intent: Intent) {
            for (action in intentFilter.actionsIterator()) {
                if (action == intent.action) {
                    getInstance(context)?.onBroadcastReceived(this, context, intent)
                    break
                }
            }
        }
    }

    private class AppProcessStartReceiver(context: Context) :
        ExplicitBroadcastReceiver(TAG(AppProcessStartReceiver::class.java), context) {
        override val intentFilter: IntentFilter
            get() {
                val intentFilter = IntentFilter()
                /**
                 * <p>
                 * Test on emulator:
                 * 1) adb kill-server
                 * 2) adb root
                 * 3) adb shell am broadcast -a android.intent.action.MY_PACKAGE_REPLACED -n com.github.paulpv.androidbletool/com.github.paulpv.androidbletool.BleTool.AppReplacedReceiver
                 */
                intentFilter.addAction(Intent.ACTION_MY_PACKAGE_REPLACED)
                return intentFilter
            }
    }

    private class AppProcessRunningStateChangeReceiver(context: Context) :
        ExplicitBroadcastReceiver(TAG(AppProcessRunningStateChangeReceiver::class.java), context) {
        override val intentFilter: IntentFilter
            get() {
                val intentFilter = IntentFilter()
                intentFilter.addAction(Intent.ACTION_POWER_CONNECTED)
                intentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED)
                intentFilter.addAction(Intent.ACTION_SCREEN_OFF)
                intentFilter.addAction(Intent.ACTION_SCREEN_ON)
                intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                return intentFilter
            }
    }

    class BleDeviceScanReceiver : BroadcastReceiver() {
        companion object {
            private val ACTION = "${ReflectionUtils.getClassName(BleDeviceScanReceiver::class.java)}.ACTION"

            fun newPendingIntent(context: Context, requestCode: Int): PendingIntent {
                val intent = Intent(context, BleDeviceScanReceiver::class.java)
                intent.action = ACTION
                return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            }
        }

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION -> {
                    getInstance(context)?.onScanResultReceived(context, intent)
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

    private val looper = looper ?: Looper.getMainLooper()
    private val scanningPendingIntent = BleDeviceScanReceiver.newPendingIntent(application, SCAN_RECEIVER_REQUEST_CODE)
    private var scanningCallbacks = object : ScanCallback() {
        override fun onScanFailed(errorCode: Int) {
            this@BleTool.onScanFailed("", errorCode)
        }

        override fun onScanResult(callbackType: Int, scanResult: ScanResult?) {
            this@BleTool.onScanResult("", callbackType, scanResult)
        }

        override fun onBatchScanResults(scanResults: MutableList<ScanResult>?) {
            this@BleTool.onBatchScanResults("", scanResults)
        }
    }

    private var notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var foregroundService: NotificationService? = null
    private var foregroundNotification: Notification? = null
    private val notificationServiceConnection: ServiceConnection

    private val handler = Handler(this.looper, Handler.Callback { msg -> this@BleTool.handleMessage(msg) })
    private var bluetoothAdapter = BluetoothUtils.getBluetoothAdapter(application)

    @Suppress("MemberVisibilityCanBePrivate")
    val isBluetoothLowEnergySupported: Boolean
        get() = BluetoothUtils.isBluetoothLowEnergySupported(application)

    @Suppress("MemberVisibilityCanBePrivate")
    val isBluetoothEnabled: Boolean
        get() = BluetoothUtils.isBluetoothAdapterEnabled(bluetoothAdapter)

    @Suppress("PrivatePropertyName")
    private var _USE_SCAN_API_VERSION = Build.VERSION.SDK_INT

    @Suppress("PropertyName")
    var USE_SCAN_API_VERSION: Int
        get() = _USE_SCAN_API_VERSION
        set(value) {
            val wasActivelyScanning = isActivelyScanning
            if (wasActivelyScanning) {
                persistentScanningPause("USE_SCAN_API_VERSION", false)
            }
            _USE_SCAN_API_VERSION = value
            if (wasActivelyScanning) {
                persistentScanningResume("USE_SCAN_API_VERSION", false)
            }
        }

    private var scanSettings = newScanSettings()
    private var scanFilters = newScanFilters()
    private var isActivelyScanning = false

    private var resumeWorkRequest: OneTimeWorkRequest? = null
    private val workManager = WorkManager.getInstance(application)

    private val broadcastReceivers = arrayOf(
        AppProcessStartReceiver(application),
        AppProcessRunningStateChangeReceiver(application)
    )

    private val recentlyNearbyDevices: ExpiringIterableLongSparseArray<BleScanResult> =
        ExpiringIterableLongSparseArray("recentlyNearbyDevices", deviceScanTimeoutMillis, this.looper)

    val recentlyNearbyDevicesIterator: Iterator<ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>>
        get() = recentlyNearbyDevices.iterateValues()

    @Suppress("PrivatePropertyName")
    private val PREFS_FILENAME = "com.github.paulpv.androidbletool.BleTool.prefs"

    @Suppress("PrivatePropertyName")
    private val PREF_PERSISTENT_SCANNING_STARTED_MILLIS = "persistentScanningStartedMillis"

    @Suppress("PrivatePropertyName")
    private val PREF_PERSISTENT_SCANNING_BACKGROUND_PID = "persistentScanningBackgroundPid"
    private val sharedPreferences = application.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)

    @Suppress("PrivatePropertyName")
    private val PERSISTENT_SCANNING_STARTED_MILLIS_UNDEFINED = 0L
    private var persistentScanningStartedMillis: Long
        get() = sharedPreferences!!.getLong(PREF_PERSISTENT_SCANNING_STARTED_MILLIS, PERSISTENT_SCANNING_STARTED_MILLIS_UNDEFINED)
        private set(value) = sharedPreferences!!.edit(commit = true) { putLong(PREF_PERSISTENT_SCANNING_STARTED_MILLIS, value) }

    @Suppress("PrivatePropertyName")
    private val PERSISTENT_SCANNING_BACKGROUND_PID_UNDEFINED = 0
    private var persistentScanningBackgroundPid: Int
        get() {
            @Suppress("UnnecessaryVariable")
            val value = sharedPreferences!!.getInt(PREF_PERSISTENT_SCANNING_BACKGROUND_PID, PERSISTENT_SCANNING_BACKGROUND_PID_UNDEFINED)
            @Suppress("SimplifyBooleanWithConstants")
            if (false && BuildConfig.DEBUG) {
                Log.e(TAG, "#PID get persistentScanningBackgroundPid=$value")
            }
            return value
        }
        set(value) {
            @Suppress("SimplifyBooleanWithConstants")
            if (false && BuildConfig.DEBUG) {
                Log.e(TAG, "#PID set persistentScanningBackgroundPid=$value")
            }
            sharedPreferences!!.edit(commit = true) { putInt(PREF_PERSISTENT_SCANNING_BACKGROUND_PID, value) }
        }

    private fun persistentScanningReset() {
        persistentScanningResumeCount = 0
        persistentScanningBackgroundPid = PERSISTENT_SCANNING_BACKGROUND_PID_UNDEFINED
        persistentScanningStartedMillis = PERSISTENT_SCANNING_STARTED_MILLIS_UNDEFINED
    }

    @Suppress("MemberVisibilityCanBePrivate")
    val persistentScanningElapsedMillis: Long
        get() {
            val persistentScanningStartedMillis = this.persistentScanningStartedMillis
            return if (persistentScanningStartedMillis != PERSISTENT_SCANNING_STARTED_MILLIS_UNDEFINED) {
                SystemClock.uptimeMillis() - persistentScanningStartedMillis
            } else {
                -1L
            }
        }

    private var persistentScanningResumeCount = 0L

    @Suppress("MemberVisibilityCanBePrivate")
    val isPersistentScanningEnabled: Boolean
        get() {
            return persistentScanningStartedMillis != PERSISTENT_SCANNING_STARTED_MILLIS_UNDEFINED
        }

    init {
        Log.i(TAG, "+init")

        if (DEBUG_FORCE_PERSISTENT_SCANNING_RESET) {
            Log.e(TAG, "init: DEBUG persistentScanningReset()")
            persistentScanningReset()
        }

        @Suppress("SimplifyBooleanWithConstants")
        if (false && BuildConfig.DEBUG) {
            USE_SCAN_API_VERSION = 25 // For debugging purposes only
        }

        // There could be a resume pending in the OS; nuke it
        workManager.cancelAllWork()

        for (broadcastReceiver in broadcastReceivers) {
            broadcastReceiver.register()
        }

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
                this@BleTool.onActivityCreated(activity!!)
            }

            override fun onActivityStarted(activity: Activity?) {
                this@BleTool.onActivityStarted(activity!!)
            }

            override fun onActivityResumed(activity: Activity?) {
                this@BleTool.onActivityResumed(activity!!)
            }

            override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {
                this@BleTool.onActivitySaveInstanceState(activity!!, outState)
            }

            override fun onActivityPaused(activity: Activity?) {
                this@BleTool.onActivityPaused(activity!!)
            }

            override fun onActivityStopped(activity: Activity?) {
                this@BleTool.onActivityStopped(activity!!)
            }

            override fun onActivityDestroyed(activity: Activity?) {
                this@BleTool.onActivityDestroyed(activity!!)
            }
        })

        recentlyNearbyDevices.addListener(object :
            ExpiringIterableLongSparseArray.ExpiringIterableLongSparseArrayListener<BleScanResult> {
            override fun onItemAdded(key: Long, index: Int, item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) =
                this@BleTool.onDeviceAdded(item)

            override fun onItemUpdated(key: Long, index: Int, item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) =
                this@BleTool.onDeviceUpdated(item)

            override fun onItemExpiring(key: Long, index: Int, item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>): Boolean =
                this@BleTool.onDeviceExpiring(item)

            override fun onItemRemoved(key: Long, index: Int, item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) =
                this@BleTool.onDeviceRemoved(item)
        })

        if (Build.VERSION.SDK_INT >= 26) {
            createNotificationChannel(
                notificationManager,
                SCANNING_NOTIFICATION_CHANNEL_ID,
                SCANNING_NOTIFICATION_CHANNEL_NAME,
                NotificationManagerCompat.IMPORTANCE_LOW,
                configuration.scanningNotificationInfo.channelDescription
            )
        }

        notificationServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val notificationService = (service as NotificationService.NotificationBinder).getServiceInstance()
                onNotificationServiceConnected(notificationService)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                onNotificationServiceDisconnected()
            }
        }

        val service = Intent(application, NotificationService::class.java)
        application.bindService(service, notificationServiceConnection, Context.BIND_AUTO_CREATE)

        Log.i(TAG, "-init")
    }

    private fun shutdown(runThenKillProcess: (() -> Unit)? = null) {
        persistentScanningStop()

        for (broadcastReceiver in broadcastReceivers) {
            broadcastReceiver.unregister()
        }

        application.unbindService(notificationServiceConnection)

        if (runThenKillProcess != null) {
            runThenKillProcess()
            exitProcess(1)
        }
    }

    //
    //
    //

    private fun getString(resId: Int, vararg formatArgs: Any): String {
        return application.getString(resId, formatArgs)
    }

    //
    //
    //

    /**
     * NOTE: Will be called after process create.
     * Process may be created due to the following reasons:
     *  1) Normal user solicited app launch
     *  2) onBootCompletedReceived
     *  3) onAppReplacedReceived
     *  4) ...
     *  N) backgroundScanner is active and process is forced closed
     *      Force close can happen for the following reasons:
     *          1) Normal user solicited
     *          2) Change permissions
     *          3) App update
     *          4) ...
     */
    private fun onNotificationServiceConnected(notificationService: NotificationService) {
        Log.i(TAG, "onNotificationServiceConnected(...)")
        foregroundService = notificationService
        Log.i(TAG, "onNotificationServiceConnected: isPersistentScanningEnabled=$isPersistentScanningEnabled")
        persistentScanningStartIfEnabled("onNotificationServiceConnected")
    }

    /**
     * Should be only ever called from [shutdown]'s call to [application]'s [android.content.Context.unbindService]
     */
    private fun onNotificationServiceDisconnected() {
        Log.i(TAG, "onNotificationServiceDisconnected(...)")
        foregroundService = null
    }

    //
    //
    //

    internal fun onBroadcastReceived(receiver: BroadcastReceiver, context: Context, intent: Intent) {
        Log.i(TAG, "onBroadcastReceived(receiver=$receiver, context=$context, intent=$intent)")
        when (receiver) {
            is BootCompletedReceiver, is AppProcessStartReceiver -> onAppProcessStartReceived(context, intent)
            is AppProcessRunningStateChangeReceiver -> onAppProcessRunningStateChangeReceived(context, intent)
        }
    }

    private fun onAppProcessStartReceived(context: Context, intent: Intent) {
        Log.i(TAG, "onAppProcessStartReceived(context=$context, intent=$intent)")
        // Process is starting up; Scanning will be started in onNotificationServiceConnected if needed
    }

    private fun onAppProcessRunningStateChangeReceived(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothAdapter.ACTION_STATE_CHANGED ->
                onBluetoothAdapterStateChangeReceived(context, intent)
            else ->
                persistentScanningResumeIfEnabled("onAppProcessRunningStateChangeReceived(${intent.action})", true)
        }
    }

    private fun onBluetoothAdapterStateChangeReceived(context: Context, intent: Intent) {
        Log.i(TAG, "onBluetoothAdapterStateChangeReceived(context=$context, intent=$intent)")
        val stateCurrent = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
        Log.i(TAG, "onBluetoothAdapterStateChangeReceived: stateCurrent=${BluetoothUtils.bluetoothAdapterStateToString(stateCurrent)}")
        @Suppress("SimplifyBooleanWithConstants")
        if (false && BuildConfig.DEBUG) {
            val statePrevious = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1)
            Log.i(TAG, "onBluetoothAdapterStateChangeReceived: statePrevious=${BluetoothUtils.bluetoothAdapterStateToString(statePrevious)}")
        }
        Log.i(TAG, "onBluetoothAdapterStateChangeReceived: isBluetoothEnabled=$isBluetoothEnabled")
        when (stateCurrent) {
            BluetoothAdapter.STATE_ON -> onBluetoothAdapterTurnedOn()
            else -> onBluetoothAdapterTurnedOff()
        }
    }

    private fun onBluetoothAdapterTurnedOn() {
        Log.i(TAG, "onBluetoothAdapterTurnedOff: BT  ON; persistentScanningResumeIfEnabled(...)")
        persistentScanningResumeIfEnabled("onBluetoothAdapterTurnedOn", true)
    }

    private fun onBluetoothAdapterTurnedOff() {
        Log.i(TAG, "onBluetoothAdapterTurnedOff: BT OFF; persistentScanningPauseIfEnabled(...)")
        persistentScanningPauseIfEnabled("onBluetoothAdapterTurnedOff", true)
    }

    //
    //
    //

    @RequiresApi(26)
    private fun createNotificationChannel(
        notificationManager: NotificationManager,
        @Suppress("SameParameterValue") channelID: String,
        @Suppress("SameParameterValue") channelName: String,
        @Suppress("SameParameterValue") channelImportance: Int,
        channelDescription: String
    ) {
        val channel = NotificationChannel(channelID, channelName, channelImportance)
        channel.description = channelDescription
        notificationManager.createNotificationChannel(channel)
    }

    private val notificationIcon: Int
        get() = configuration.scanningNotificationInfo.getSmallIcon(isForegrounded)

    private val notificationText: String
        get() = configuration.scanningNotificationInfo.getContentText(isBluetoothEnabled)

    private fun createNotification(
        context: Context,
        @Suppress("SameParameterValue") channelID: String,
        @Suppress("SameParameterValue") requestCode: Int,
        text: String
    ): Notification {
        val intent = Intent(application, configuration.scanningNotificationInfo.activityClass)
        val pendingIntent = PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(context, channelID)
            .setSmallIcon(notificationIcon)
            .setContentTitle(configuration.scanningNotificationInfo.contentTitle)
            .setContentText(text)
            .setShowWhen(false)
            .setChannelId(channelID)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun scanningNotificationUpdate() {
        if (foregroundService == null) {
            Log.w(TAG, "showScanningNotification: Unexpected foregroundService == null; ignoring")
            return
        }
        if (isPersistentScanningEnabled) {
            val notification = createNotification(application, SCANNING_NOTIFICATION_CHANNEL_ID, SCANNING_NOTIFICATION_REQUEST_CODE, notificationText)

            if (foregroundNotification == null) {
                foregroundNotification = notification
                //
                // Prevent Android OS from suspending this app's process
                //
                foregroundService!!.startForeground(SCANNING_NOTIFICATION_ID, foregroundNotification)
            } else {
                foregroundNotification = notification
                notificationManager.notify(SCANNING_NOTIFICATION_ID, foregroundNotification)
            }
        } else {
            foregroundNotification = null
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

    private var currentActivity: Activity? = null
    private val deviceScanObservers: MutableSet<DeviceScanObserver> = mutableSetOf()

    private val isForegrounded: Boolean
        get() = currentActivity != null

    private fun onActivityCreated(activity: Activity) {
        Log.e(TAG, "onActivityCreated(activity=$activity)")
        activityAdd(activity)
    }

    private fun onActivityStarted(activity: Activity) {
        Log.e(TAG, "onActivityStarted(activity=$activity)")
        activityAdd(activity)
    }

    private fun onActivityResumed(activity: Activity) {
        Log.e(TAG, "onActivityResumed(activity=$activity)")
        activityAdd(activity)
    }

    private fun onActivitySaveInstanceState(activity: Activity, outState: Bundle?) {
        Log.e(TAG, "onActivitySaveInstanceState(activity=$activity, outState=$outState)")
        //...
    }

    private fun onActivityPaused(activity: Activity) {
        Log.e(TAG, "onActivityPaused(activity=$activity)")
        activityRemove(activity)
    }

    private fun onActivityStopped(activity: Activity) {
        Log.e(TAG, "onActivityStopped(activity=$activity)")
        activityRemove(activity)
    }

    private fun onActivityDestroyed(activity: Activity) {
        Log.e(TAG, "onActivityDestroyed(activity=$activity)")
        activityRemove(activity)
    }

    private fun activityAdd(activity: Activity) {
        currentActivity = activity
        if (activity is DeviceScanObserver) {
            attach(activity)
        }
    }

    private fun activityRemove(activity: Activity) {
        currentActivity = null
        if (activity is DeviceScanObserver) {
            detach(activity)
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun attach(observer: DeviceScanObserver) {
        Log.v(TAG, "attach(observer=$observer)")
        if (deviceScanObservers.add(observer)) {
            //...
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun detach(observer: DeviceScanObserver) {
        Log.v(TAG, "attach(observer=$observer)")
        if (deviceScanObservers.remove(observer)) {
            //...
        }
    }

    //
    //
    //

    fun persistentScanningEnable(enable: Boolean): Boolean {
        Log.i(TAG, "persistentScanningEnable(enable=$enable)")
        return if (enable) {
            persistentScanningStart()
        } else {
            persistentScanningStop()
        }
    }

    private fun persistentScanningStartIfEnabled(caller: String) {
        if (isPersistentScanningEnabled) {
            Log.i(TAG, "$caller->persistentScanningStartIfEnabled; isPersistentScanningEnabled == true; START")
            persistentScanningStart()
        } else {
            Log.i(TAG, "$caller->persistentScanningStartIfEnabled; isPersistentScanningEnabled == false; ignoring")
        }
    }

    private fun persistentScanningResumeIfEnabled(caller: String, updateScanningNotification: Boolean) {
        if (isPersistentScanningEnabled) {
            //Log.i(TAG, "$caller->persistentScanningResumeIfEnabled; isPersistentScanningEnabled == true; RESUME")
            @Suppress("UNUSED_VARIABLE") val success = persistentScanningResume(caller, updateScanningNotification)
            //Log.i(TAG, "persistentScanningResumeIfEnabled: success=$success")
            /*
            if (success) {
                Log.i(TAG, "persistentScanningResumeIfEnabled: success")
            } else {
                Log.e(TAG, "persistentScanningResumeIfEnabled: failed")
                // TODO:(pv) notify unable to resume
            }
            */
        } else {
            //Log.i(TAG, "$caller->persistentScanningResumeIfEnabled; isPersistentScanningEnabled == false; ignoring")
        }
    }

    private fun persistentScanningPauseIfEnabled(caller: String, updateScanningNotification: Boolean) {
        if (isPersistentScanningEnabled) {
            //Log.i(TAG, "$caller->persistentScanningPauseIfEnabled; isPersistentScanningEnabled == true; PAUSE")
            @Suppress("UNUSED_VARIABLE") val success = persistentScanningPause(caller, updateScanningNotification)
            //Log.i(TAG, "persistentScanningPauseIfEnabled: success=$success")
            /*
            if (success) {
                Log.i(TAG, "persistentScanningPauseIfEnabled: success")
            } else {
                Log.e(TAG, "persistentScanningPauseIfEnabled: failed")
                // TODO:(pv) notify unable to pause
            }
            */
        } else {
            //Log.i(TAG, "$caller->persistentScanningPauseIfEnabled; isPersistentScanningEnabled == false; ignoring")
        }
    }

    @Suppress("PropertyName", "PrivatePropertyName")
    private val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    /*
     * Checks for Permissions and then calls persistentScanningResume(...)
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun persistentScanningStart(): Boolean {
        Log.i(TAG, "persistentScanningStart()")

        val runAfterPermissionsCheck = {
            recentlyNearbyDevices.clear()
            if (persistentScanningResume("persistentScanningStart", false)) {
                persistentScanningStartedMillis = SystemClock.uptimeMillis()
                scanningNotificationUpdate()
            }
        }

        if (PermissionsUtil.hasSelfPermission(application, PERMISSIONS)) {
            runAfterPermissionsCheck()
            return isPersistentScanningEnabled
        }

        // TODO:(pv) Handle permissions, including denied

        if (currentActivity != null) {
            currentActivity.runWithPermissions(
                *PERMISSIONS
                /*
                , options = QuickPermissionsOptions(
                    handleRationale = false,
                    rationaleMessage = "Custom rational message",
                    permanentlyDeniedMessage = "Custom permanently denied message",
                    rationaleMethod = { req ->
                        {
                        }
                    },
                    permanentDeniedMethod = { req ->
                        {
                        }
                    }
                )*/
            ) {
                runAfterPermissionsCheck()
            }
            return true
        }

        //  TODO:pv) No handlers: Show **persistent** notification [to keep app process alive] requesting permission(s)
        //...

        return true
    }

    private fun newScanSettings(): ScanSettings {
        val builder = ScanSettings.Builder()
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        if (Build.VERSION.SDK_INT >= 23) {
            builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        }
        return builder.build()
    }

    private fun newScanFilters(): List<ScanFilter> {
        val scanFilters = mutableListOf<ScanFilter>()
        scanFilters.addAll(configuration.SCAN_FILTERS)
        if (scanFilters.isEmpty()) {
            scanFilters.add(SCAN_FILTER_EMPTY)
        }
        return scanFilters
    }

    /*
     * Does *NOT* check for Permissions!
     */
    @SuppressLint("NewApi")
    private fun persistentScanningResume(caller: String, updateScanningNotification: Boolean): Boolean {
        Log.i(TAG, "persistentScanningResume(${Utils.quote(caller)}, updateScanningNotification=$updateScanningNotification)")

        if (USE_RESUME_PAUSE_HANDLER) {
            delayedScanningRemoveAll()
        }

        recentlyNearbyDevices.resume()

        var success = false

        if (isBluetoothLowEnergySupported) {
            if (isBluetoothEnabled) {

                persistentScanningResumeCount++
                // @formatter:off
                Log.i(TAG, "persistentScanningResume: persistentScanningElapsedMillis=$persistentScanningElapsedMillis, persistentScanningResumeCount=$persistentScanningResumeCount")
                // @formatter:on

                try {
                    if (USE_SCAN_API_VERSION >= 26) {
                        Log.i(TAG, "persistentScanningResume: USE_API_VERSION >= 26; Start background PendingIntent scan")
                        bluetoothAdapter!!.bluetoothLeScanner.startScan(scanFilters, scanSettings, scanningPendingIntent)
                        persistentScanningBackgroundPid = MY_PID
                    } else {
                        Log.i(TAG, "persistentScanningResume: USE_API_VERSION < 26; Start non-background ScanCallback scan")
                        bluetoothAdapter!!.bluetoothLeScanner.startScan(scanFilters, scanSettings, scanningCallbacks)
                    }

                    // TODO:(pv) Find a way to get auto-start after reboot to work without using NotificationService.
                    //      Then we truly only have to show notification if API < 26.

                    isActivelyScanning = true

                    success = true

                } catch (scanException: Throwable) {
                    Log.e(TAG, "persistentScanningResume: Failed to start scan", scanException)
                    onScanFailed("persistentScanningResume", scanException)
                }
            }
        }

        if (updateScanningNotification) {
            scanningNotificationUpdate()
        }

        if (success) {// && isPersistentScanningEnabled) {
            if (USE_RESUME_PAUSE_HANDLER) {
                delayedScanningPauseAdd()
            }
        }

        return success
    }

    @SuppressLint("NewApi")
    private fun persistentScanningPause(caller: String, updateScanningNotification: Boolean): Boolean {
        Log.i(TAG, "persistentScanningPause(caller=$caller, updateScanningNotification=$updateScanningNotification)")

        if (USE_RESUME_PAUSE_HANDLER) {
            delayedScanningRemoveAll()
        }

        recentlyNearbyDevices.pause()

        var success = false

        // TODO:(pv) Test pause/resume when BT is off for exceptions/errors

        try {

            isActivelyScanning = false

            if (USE_SCAN_API_VERSION >= 26) {
                Log.i(TAG, "persistentScanningPause: USE_API_VERSION >= 26; Stopping background PendingIntent scan")
                bluetoothAdapter!!.bluetoothLeScanner.stopScan(scanningPendingIntent)
            } else {
                Log.i(TAG, "persistentScanningPause: USE_API_VERSION < 26; Stopping non-background ScanCallback scan")
                bluetoothAdapter!!.bluetoothLeScanner.stopScan(scanningCallbacks)
            }

            success = true

        } catch (scanException: BleScanException) {
            Log.e(TAG, "persistentScanningPause: Failed to stop scan", scanException)
            onScanFailed("persistentScanningPause", scanException)
        }

        if (updateScanningNotification) {
            scanningNotificationUpdate()
        }

        if (isPersistentScanningEnabled) {
            // TODO:(pv) Reconsider only emmitting devices when paused
            //...
            devicesUpdatedDebounce()
            if (success) {
                if (USE_RESUME_PAUSE_HANDLER) {
                    delayedScanningResumeAdd()
                }
            }
        }

        return success
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun persistentScanningStop(): Boolean {
        Log.i(TAG, "persistentScanningStop()")
        if (!isPersistentScanningEnabled) return false
        persistentScanningReset()
        return persistentScanningPause("persistentScanningStop", true)
    }

    //
    //region RESUME_PAUSE_HANDLER
    //

    private val USE_RESUME_PAUSE_HANDLER = true

    /**
     * 2019/10/11
     * https://developer.android.com/topic/libraries/architecture/workmanager/how-to/recurring-work
     * "Note: The minimum repeat interval that can be defined is 15 minutes (same as the JobScheduler API)."
     */
    @Suppress("PrivatePropertyName")
    private val MINIMUM_RELIABLE_WORK_REQUEST_DELAY_MILLIS = 15 * 60 * 1000L // 15 minutes

    @Suppress("PrivatePropertyName")
    private val DELAYED_SCANNING_FAILSAFE_RESUME_MILLIS = MINIMUM_RELIABLE_WORK_REQUEST_DELAY_MILLIS

    /**
     * Runs MINIMUM_RELIABLE_WORK_REQUEST_DELAY_MILLIS after Pause to pseudo-ensure that scanning is resumed
     */
    class ResumeWorker(private val context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            @Suppress("RemoveRedundantQualifierName")
            getInstance(context)?.persistentScanningResumeIfEnabled("ResumeWorker", false)
            return Result.success()
        }
    }

    @Suppress("PrivatePropertyName")
    private val MESSAGE_WHAT_PAUSE = 100

    @Suppress("PrivatePropertyName")
    private val MESSAGE_WHAT_RESUME = 101

    private fun handleMessage(msg: Message?): Boolean {
        val what = msg?.what
        //Log.i(TAG, "handleMessage: msg.what=$what")
        var handled = false
        when (what) {
            MESSAGE_WHAT_PAUSE -> {
                persistentScanningPauseIfEnabled("handleMessage", false)
                handled = true
            }
            MESSAGE_WHAT_RESUME -> {
                persistentScanningResumeIfEnabled("handleMessage", false)
                handled = true
            }
        }
        return handled
    }

    private fun delayedScanningResumeAdd() {
        resumeWorkRequest = OneTimeWorkRequest.Builder(ResumeWorker::class.java)
            .setInitialDelay(DELAYED_SCANNING_FAILSAFE_RESUME_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueue(resumeWorkRequest!!)

        handler.sendEmptyMessageDelayed(MESSAGE_WHAT_RESUME, AndroidBleScanStartLimits.scanStartIntervalAverageSafeMillis)
    }

    private fun delayedScanningResumeRemove() {
        handler.removeMessages(MESSAGE_WHAT_RESUME)
        if (resumeWorkRequest != null) {
            workManager.cancelWorkById(resumeWorkRequest!!.id)
            resumeWorkRequest = null
        }
    }

    private fun delayedScanningPauseAdd() {
        handler.sendEmptyMessageDelayed(MESSAGE_WHAT_PAUSE, AndroidBleScanStartLimits.scanStartIntervalAverageSafeMillis)
    }

    private fun delayedScanningPauseRemove() {
        handler.removeMessages(MESSAGE_WHAT_PAUSE)
    }

    private fun delayedScanningRemoveAll() {
        delayedScanningResumeRemove()
        delayedScanningPauseRemove()
    }

    //
    //endregion
    //

    private var scanResultLogged = false

    @SuppressLint("NewApi")
    fun onScanResultReceived(@Suppress("UNUSED_PARAMETER") context: Context, intent: Intent) {
        //PbLog.e(TAG, "onScanResultReceived(context=" + context + ", intent=" + PbPlatformUtils.toString(intent) + ')');

        val persistentScanningBackgroundPid = persistentScanningBackgroundPid

        @Suppress("SimplifyBooleanWithConstants")
        if (false && BuildConfig.DEBUG) {
            // @formatter:off
            Log.w(TAG, "#PID onScanResultReceived: persistentScanningBackgroundPid=$persistentScanningBackgroundPid, MY_PID=$MY_PID")
            // @formatter:on
        }

        if (persistentScanningBackgroundPid == PERSISTENT_SCANNING_BACKGROUND_PID_UNDEFINED) {
            if (!scanResultLogged) {
                scanResultLogged = true
                @Suppress("SimplifyBooleanWithConstants")
                if (true && BuildConfig.DEBUG) {
                    // @formatter:off
                    Log.w(TAG, "#PID onScanResultReceived: persistentScanningBackgroundPid UNDEFINED; ignoring")
                    // @formatter:on
                }
            }
            return
        }

        if (persistentScanningBackgroundPid != MY_PID) {
            @Suppress("SimplifyBooleanWithConstants")
            if (true && BuildConfig.DEBUG) {
                // @formatter:off
                Log.w(TAG, "#PID onScanResultReceived: persistentScanningBackgroundPid($persistentScanningBackgroundPid) != android.os.Process.myPid()($MY_PID); Scan is orphaned/leaked and cannot be stopped! :(")
                // @formatter:on
            }
            // TODO:(pv) Notify user to reset bluetooth or reboot phone
            // ... OR AUTOMATICALLY RESET BLUETOOTH?
            // TODO:(pv) Test if turning bluetooth off and back on ACTUALLY fixes this situation
        } else {
            @Suppress("SimplifyBooleanWithConstants")
            if (false && BuildConfig.DEBUG) {
                // @formatter:off
                Log.i(TAG, "#PID onScanResultReceived: persistentScanningBackgroundPid($persistentScanningBackgroundPid) == android.os.Process.myPid()($MY_PID); Scan is **NOT** orphaned/leaked and can be stopped! :)")
                // @formatter:on
            }
        }

        //PbLog.e(TAG, "onScanReceived(context=" + context + ", intent=" + PbPlatformUtils.toString(intent) + ')');

        val errorCode = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, -1)
        if (errorCode != -1) {
            onScanFailed("PendingIntent", errorCode)
            return
        }

        val scanResults = intent.getParcelableArrayListExtra<ScanResult>(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)
        @Suppress("FoldInitializerAndIfToElvis")
        if (scanResults == null) {
            return
        }

        val callbackType = intent.getIntExtra(BluetoothLeScanner.EXTRA_CALLBACK_TYPE, -1)
        if (callbackType == -1) {
            onBatchScanResults("PendingIntent", scanResults)
        } else {
            for (scanResult in scanResults) {
                onScanResult("PendingIntent", callbackType, scanResult)
            }
        }

        /*
        try {
            val scanResult = rxBleClient.backgroundScanner.onScanResultReceived(intent)
            //Log.i(TAG, "onScanResultReceived: Scan results received: $scanResults")
            scanResult.forEach { processScanResult(it) }
        } catch (scanException: Throwable) {
            Log.e(TAG, "Failed to scan devices", scanException)
            onScanError(scanException)
        }
        */
    }

    private fun onScanFailed(caller: String, errorCode: Int) {
        onScanFailed(caller, BluetoothUtils.BleScanThrowable(errorCode))
    }

    private fun onScanFailed(caller: String, throwable: Throwable) {
        Log.e(TAG, "${Utils.getTimeDurationFormattedString(persistentScanningElapsedMillis)} onScanFailed: $caller $throwable")
        deviceScanObservers.forEach { it.onDeviceScanError(this, throwable) }
    }

    private fun onBatchScanResults(caller: String, scanResults: MutableList<ScanResult>?) {
        if (scanResults == null) return
        /*
        val msg = "onBatchScanResults($caller, scanResults(${scanResults.size})=..."
        try {
            Log.v(TAG, "+$msg")
         */
        for (scanResult in scanResults) {
            onScanResult("$caller->onBatchScanResults", ScanSettings.CALLBACK_TYPE_ALL_MATCHES, scanResult)
        }
        /*
        } finally {
            Log.v(TAG, "-$msg")
        }
        */
    }

    private fun onScanResult(caller: String, callbackType: Int, scanResult: ScanResult?) {
        if (scanResult == null) return
        val bleDevice = scanResult.device
        val macAddressString = bleDevice.address
        if (!debugDeviceAddressFilterPass(macAddressString)) {
            //Log.w(TAG, "onScanResult: DEBUG_DEVICE_ADDRESS_FILTER.contains($macAddressString) == false; ignoring")
            return
        }
        if (false && BuildConfig.DEBUG) {
            Log.v(TAG, "onScanResult: caller=$caller, callbackType=${callbackTypeToString(callbackType)}, scanResult=$scanResult")
        }
        val macAddressLong = BluetoothUtils.macAddressStringToLong(macAddressString)
        if (false) {
            // TODO:(pv) Find a way to do this without it resulting in calling onDeviceRemoved
            var bleScanResult = recentlyNearbyDevices.remove(macAddressLong)
            bleScanResult = BleScanResult(bleScanResult, scanResult)
            recentlyNearbyDevices.put(macAddressLong, bleScanResult)
        } else {
            var bleScanResult = recentlyNearbyDevices.get(macAddressLong)
            if (bleScanResult == null) {
                bleScanResult = BleScanResult(scanResult)
            } else {
                bleScanResult.update(scanResult)
            }
            // BUG:(pv) Does this sometimes incorrectly re-sort the collection?!?!?!
            recentlyNearbyDevices.put(macAddressLong, bleScanResult)
        }
    }

    private fun onDeviceAdded(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        val bleScanResult = item.value
        val scanResult = bleScanResult.scanResult
        val bleDevice = scanResult.device
        val macAddressString = bleDevice.address
        //val rssi = bleScanResult.rssi
        // @formatter:off
        Log.i(TAG, "${Utils.getTimeDurationFormattedString(persistentScanningElapsedMillis)} $macAddressString onDeviceAdded: ADDED! bleScanResult=$bleScanResult")
        // @formatter:on
        deviceScanObservers.forEach { it.onDeviceAdded(this, item) }
    }


    private fun onDeviceUpdated(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        if (true) {
            val bleScanResult = item.value
            val scanResult = bleScanResult.scanResult
            val bleDevice = scanResult.device
            val macAddressString = bleDevice.address
            val ageMillis = item.addedElapsedMillis
            // @formatter:off
            Log.v(TAG, "${Utils.getTimeDurationFormattedString(persistentScanningElapsedMillis)} $macAddressString onDeviceUpdated: UPDATED! ageMillis=${Utils.getTimeDurationFormattedString(ageMillis)}, bleScanResult=$bleScanResult")
            // @formatter:on
        }
        recentlyNearbyDevicesUpdatedDebounce.add(item)
    }

    private val recentlyNearbyDevicesUpdatedDebounce: MutableSet<ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>> = mutableSetOf()

    private fun devicesUpdatedDebounce() {
        Log.e(TAG, "devicesUpdatedDebounce()")
        val it = recentlyNearbyDevicesUpdatedDebounce.iterator()
        while (it.hasNext()) {
            val item = it.next()
            deviceScanObservers.forEach { observer ->
                observer.onDeviceUpdated(this, item)
            }
            it.remove()
        }
    }

    private fun onDeviceExpiring(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>): Boolean {
        val bleScanResult = item.value
        val scanResult = bleScanResult.scanResult
        val bleDevice = scanResult.device
        val macAddressString = bleDevice.address
        val timeoutMillis = item.timeoutMillis
        // @formatter:off
        Log.w(TAG, "${Utils.getTimeDurationFormattedString(persistentScanningElapsedMillis)} $macAddressString onDeviceExpiring: timeoutMillis=$timeoutMillis")
        //Log.w(TAG, "${Utils.getTimeDurationFormattedString(persistentScanningElapsedMillis)} $macAddressString onDeviceExpiring: EXPIRING...")
        Log.w(TAG, "${Utils.getTimeDurationFormattedString(persistentScanningElapsedMillis)} $macAddressString onDeviceExpiring: isPersistentScanningEnabled=$isPersistentScanningEnabled")
        Log.w(TAG, "${Utils.getTimeDurationFormattedString(persistentScanningElapsedMillis)} $macAddressString onDeviceExpiring: persistentScanningElapsedMillis=$persistentScanningElapsedMillis")
        Log.w(TAG, "${Utils.getTimeDurationFormattedString(persistentScanningElapsedMillis)} $macAddressString onDeviceExpiring: isBluetoothEnabled=$isBluetoothEnabled")
        @Suppress("UnnecessaryVariable")
        val keep = !isPersistentScanningEnabled || persistentScanningElapsedMillis < deviceScanTimeoutMillis || !isBluetoothEnabled
        Log.w(TAG, "${Utils.getTimeDurationFormattedString(persistentScanningElapsedMillis)} $macAddressString onDeviceExpiring: keep=$keep")
        // @formatter:on
        return keep
    }

    private fun onDeviceRemoved(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        val bleScanResult = item.value
        val scanResult = bleScanResult.scanResult
        val bleDevice = scanResult.device
        val macAddressString = bleDevice.address
        // @formatter:off
        Log.i(TAG, "${Utils.getTimeDurationFormattedString(persistentScanningElapsedMillis)} $macAddressString onDeviceRemoved: REMOVED! bleScanResult=$bleScanResult")
        // @formatter:on
        recentlyNearbyDevicesUpdatedDebounce.remove(item)
        deviceScanObservers.forEach { it.onDeviceRemoved(this, item) }
    }

    //
    //
    //

    private val gattManager = GattManager(application, this.looper)

    fun getBleDevice(macAddress: String): BleDevice {
        return BleDevice.newDevice(gattManager, macAddress)
    }
}
