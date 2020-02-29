package com.github.paulpv.androidbletool.gatt

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import com.github.paulpv.androidbletool.BluetoothUtils.Companion.bluetoothProfileStateToString
import com.github.paulpv.androidbletool.BluetoothUtils.Companion.getBluetoothAdapter
import com.github.paulpv.androidbletool.BluetoothUtils.Companion.macAddressLongToString
import com.github.paulpv.androidbletool.BluetoothUtils.Companion.throwExceptionIfInvalidBluetoothAddress
import com.github.paulpv.androidbletool.BuildConfig
import com.github.paulpv.androidbletool.gatt.GattHandler.GattHandlerListener.DisconnectReason
import com.github.paulpv.androidbletool.gatt.GattHandler.GattHandlerListener.GattOperation
import com.github.paulpv.androidbletool.gatt.GattUtils.Companion.createBluetoothGattCharacteristic
import com.github.paulpv.androidbletool.gatt.GattUtils.Companion.safeClose
import com.github.paulpv.androidbletool.gatt.GattUtils.Companion.safeDisconnect
import com.github.paulpv.androidbletool.gatt.GattUtils.Companion.toBytes
import com.github.paulpv.androidbletool.gatt.GattUuids.Companion.toString
import com.github.paulpv.androidbletool.utils.ListenerManager
import com.github.paulpv.androidbletool.utils.MyHandler
import com.github.paulpv.androidbletool.utils.Utils.Companion.TAG
import com.github.paulpv.androidbletool.utils.Utils.Companion.formatNumber
import com.github.paulpv.androidbletool.utils.Utils.Companion.isNullOrEmpty
import com.github.paulpv.androidbletool.utils.Utils.Companion.quote
import java.util.*

/**
 * This is admittedly largely based on circa 2014 https://github.com/NordicPlayground/puck-central-android/blob/master/PuckCentral/app/src/main/java/no/nordicsemi/puckcentral/bluetooth/gatt/GattManager.java
 * That code is elegantly simple...
 * ...perhaps too simple.
 * One other problem is that it creates an AsyncTask for every operation to handle possible
 * timeout.
 * This seems very heavy.
 * The below implementation handles timeouts
 *
 *
 * One gripe I have about Nordic's code is that it routes every operation through an AsyncTask.
 * That seems *VERY* heavy.
 */
@Suppress("MemberVisibilityCanBePrivate", "unused", "SameParameterValue")
class GattHandler internal constructor(gattManager: GattManager, deviceAddress: Long) {
    companion object {
        private val TAG = TAG(GattHandler::class.java)

        var VERBOSE_LOG_CHARACTERISTIC_CHANGE = false

        var VERBOSE_LOG_PENDING_OPERATION_TIMEOUT = false
        var VERBOSE_LOG_PENDING_OPERATION_TIMEOUT_UNEXPECTED = false

        //
        // NOTE:(pv) SweetBlue is approximately 13 seconds
        //
        @Suppress("MemberVisibilityCanBePrivate")
        const val DEFAULT_CONNECT_INTERNAL_TIMEOUT_MILLIS = 9 * 1000

        var CONNECT_INTERNAL_TIMEOUT_MILLIS = DEFAULT_CONNECT_INTERNAL_TIMEOUT_MILLIS

        val DEFAULT_CONNECT_EXTERNAL_TIMEOUT_MILLIS = if (BuildConfig.DEBUG) {
            60 * 1000
        } else {
            17 * 1000
        }

        const val DEFAULT_OPERATION_TIMEOUT_MILLIS = 5 * 1000
        const val DEFAULT_DISCONNECT_TIMEOUT_MILLIS = 250

        @Suppress("MemberVisibilityCanBePrivate")
        const val DEFAULT_POST_DELAY_MILLIS = 0

        var POST_DELAY_MILLIS = DEFAULT_POST_DELAY_MILLIS

        /**
         * See https://github.com/NordicSemiconductor/Android-BLE-Library/blob/master/ble/src/main/java/no/nordicsemi/android/ble/BleManager.java#L649
         *
         * The onConnectionStateChange event is triggered just after the Android connects to a device.
         * In case of bonded devices, the encryption is reestablished AFTER this callback is called.
         * Moreover, when the device has Service Changed indication enabled, and the list of services
         * has changed (e.g. using the DFU), the indication is received few hundred milliseconds later,
         * depending on the connection interval.
         * When received, Android will start performing a service discovery operation, internally,
         * and will NOT notify the app that services has changed.
         *
         * If the gatt.discoverServices() method would be invoked here with no delay, if would return
         * cached services, as the SC indication wouldn't be received yet. Therefore, we have to
         * postpone the service discovery operation until we are (almost, as there is no such callback)
         * sure, that it has been handled. It should be greater than the time from
         * LLCP Feature Exchange to ATT Write for Service Change indication.
         *
         * If your device does not use Service Change indication (for example does not have DFU)
         * the delay may be 0.
         *
         * Please calculate the proper delay that will work in your solution.
         *
         * For devices that are not bonded, but support paiing, a small delay is required on some
         * older Android versions (Nexus 4 with Android 5.1.1) when the device will send pairing
         * request just after connection. If so, we want to wait with the service discovery until
         * bonding is complete.
         *
         * The default implementation returns 1600 ms for bonded and 300 ms when the device is not
         * bonded to be compatible with older versions of the library.
         */
        fun getServiceDiscoveryDelay(bonded: Boolean): Int {
            return if (bonded) 1600 else 300
        }

        var defaultConnectTimeoutMillis = DEFAULT_CONNECT_EXTERNAL_TIMEOUT_MILLIS
        var defaultOperationTimeoutMillis = DEFAULT_OPERATION_TIMEOUT_MILLIS
        var defaultDisconnectTimeoutMillis = DEFAULT_DISCONNECT_TIMEOUT_MILLIS

        /**
         * Well-Known UUID for [Client Characteristic Configuration](https://developer.bluetooth.org/gatt/descriptors/Pages/DescriptorViewer.aspx?u=org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml)
         */
        private val CLIENT_CHARACTERISTIC_CONFIG = GattUuids.CLIENT_CHARACTERISTIC_CONFIG.uuid

        @Suppress("SimplifyBooleanWithConstants")
        private val DEBUG_LOG_SERVICES_AND_CHARACTERISTICS = false && BuildConfig.DEBUG
    }

    /**
     * Various wrappers around [BluetoothGattCallback] methods
     */
    @Suppress("UNUSED_PARAMETER")
    abstract class GattHandlerListener {
        enum class GattOperation {
            Connect,
            DiscoverServices,
            CharacteristicRead,
            CharacteristicWrite,
            CharacteristicSetNotification,
            ReadRemoteRssi
        }

        /**
         * @param gattHandler   GattHandler
         * @param operation     GattOperation
         * @param timeoutMillis The requested timeout milliseconds
         * @param elapsedMillis The actual elapsed milliseconds
         * @return true to forcibly stay connected, false to allow disconnect
         */
        fun onDeviceOperationTimeout(
            gattHandler: GattHandler?,
            operation: GattOperation?,
            timeoutMillis: Long,
            elapsedMillis: Long
        ): Boolean {
            return false
        }

        /**
         * @param gattHandler GattHandler
         * @return true to forcibly disconnect, false to not forcibly disconnect
         */
        fun onDeviceConnecting(
            gattHandler: GattHandler?
        ): Boolean {
            return false
        }

        /**
         * @param gattHandler   GattHandler
         * @param elapsedMillis long
         * @return true to forcibly disconnect, false to not forcibly disconnect
         */
        fun onDeviceConnected(
            gattHandler: GattHandler?,
            elapsedMillis: Long
        ): Boolean {
            return false
        }

        enum class DisconnectReason {
            ConnectFailed,
            SolicitedDisconnect,
            SolicitedDisconnectTimeout,
            UnsolicitedDisconnect
        }

        /**
         * @param gattHandler   GattHandler
         * @param status        same as status in [BluetoothGattCallback.onConnectionStateChange], or -1 if unknown
         * @param reason        DisconnectReason
         * @param elapsedMillis elapsedMillis
         * @return true to automatically call [.removeListener]
         */
        fun onDeviceDisconnected(
            gattHandler: GattHandler?,
            status: Int,
            reason: DisconnectReason?,
            elapsedMillis: Long
        ): Boolean {
            return false
        }

        /**
         * @param gattHandler   GattHandler
         * @param services      List<BluetoothGattService>
         * @param success       if false, will always disconnect
         * @param elapsedMillis elapsedMillis
         * @return true to forcibly disconnect, false to not forcibly disconnect
        </BluetoothGattService> */
        fun onDeviceServicesDiscovered(
            gattHandler: GattHandler?,
            services: List<BluetoothGattService>?,
            success: Boolean,
            elapsedMillis: Long
        ): Boolean {
            return false
        }

        /**
         * @param gattHandler    GattHandler
         * @param characteristic BluetoothGattCharacteristic
         * @param success        if false, will always disconnect
         * @param elapsedMillis  elapsedMillis
         * @return true to forcibly disconnect, false to not forcibly disconnect
         */
        fun onDeviceCharacteristicRead(
            gattHandler: GattHandler?,
            characteristic: BluetoothGattCharacteristic?,
            success: Boolean,
            elapsedMillis: Long
        ): Boolean {
            return false
        }

        /**
         * @param gattHandler    GattHandler
         * @param characteristic BluetoothGattCharacteristic
         * @param success        if false, will always disconnect
         * @param elapsedMillis  elapsedMillis
         * @return true to forcibly disconnect, false to not forcibly disconnect
         */
        fun onDeviceCharacteristicWrite(
            gattHandler: GattHandler?,
            characteristic: BluetoothGattCharacteristic?,
            success: Boolean,
            elapsedMillis: Long
        ): Boolean {
            return false
        }

        /**
         * @param gattHandler    GattHandler
         * @param characteristic BluetoothGattCharacteristic
         * @param success        if false, will always disconnect
         * @param elapsedMillis  elapsedMillis
         * @return true to forcibly disconnect, false to not forcibly disconnect
         */
        fun onDeviceCharacteristicSetNotification(
            gattHandler: GattHandler?,
            characteristic: BluetoothGattCharacteristic?,
            success: Boolean,
            elapsedMillis: Long
        ): Boolean {
            return false
        }

        /**
         * @param gattHandler    GattHandler
         * @param characteristic BluetoothGattCharacteristic
         * @return true to forcibly disconnect, false to not forcibly disconnect
         */
        fun onDeviceCharacteristicChanged(
            gattHandler: GattHandler?,
            characteristic: BluetoothGattCharacteristic?
        ): Boolean {
            return false
        }

        /**
         * @param gattHandler   GattHandler
         * @param rssi          rssi
         * @param success       if false, will always disconnect
         * @param elapsedMillis elapsedMillis
         * @return true to forcibly disconnect, false to not forcibly disconnect
         */
        fun onDeviceReadRemoteRssi(
            gattHandler: GattHandler?,
            rssi: Int,
            success: Boolean,
            elapsedMillis: Long
        ): Boolean {
            return false
        }
    }

    private val gattManager: GattManager
    private val context: Context
    val deviceAddressLong: Long
    val deviceAddressString: String

    /**
     * NOTE: All calls to GattHandlerListener methods should be **INSIDE** mHandlerMain's Looper thread
     */
    private val listenerManager: ListenerManager<GattHandlerListener>
    private val handlerMain: MyHandler

    //
    //
    //

    val bluetoothAdapter: BluetoothAdapter?
    private val startTimes = mutableMapOf<GattOperation, Long>()
    private val backgroundBluetoothGattCallback: BluetoothGattCallback

    /**
     * synchronized behind gattManager
     */
    private var bluetoothGatt: BluetoothGatt? = null

    /**
     * synchronized behind gattManager
     */
    var isDisconnecting = false
        private set

    init {
        throwExceptionIfInvalidBluetoothAddress(deviceAddress)

        this.gattManager = gattManager

        context = gattManager.context
        deviceAddressLong = deviceAddress
        deviceAddressString = macAddressLongToString(deviceAddress)
        listenerManager = ListenerManager(this)
        handlerMain = MyHandler(this.gattManager.looper, Handler.Callback { msg: Message -> handleMessage(msg) })
        bluetoothAdapter = getBluetoothAdapter(context)
        backgroundBluetoothGattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                this@GattHandler.onConnectionStateChange(gatt, status, newState)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                this@GattHandler.onServicesDiscovered(gatt, status)
            }

            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                this@GattHandler.onCharacteristicRead(gatt, characteristic, status)
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                this@GattHandler.onCharacteristicWrite(gatt, characteristic, status)
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                this@GattHandler.onDescriptorWrite(gatt, descriptor, status)
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                this@GattHandler.onCharacteristicChanged(gatt, characteristic)
            }

            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                this@GattHandler.onReadRemoteRssi(gatt, rssi, status)
            }
        }
    }

    fun isBluetoothAdapterEnabled(callerName: String): Boolean {
        if (bluetoothAdapter == null) {
            Log.w(TAG, logPrefix("$callerName: mBluetoothAdapter == null; ignoring"))
            return false
        }
        return try {
            if (!bluetoothAdapter.isEnabled) {
                Log.w(TAG, logPrefix("$callerName: mBluetoothAdapter.isEnabled() == false; ignoring"))
                return false
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, logPrefix("$callerName: EXCEPTION mBluetoothAdapter.isEnabled()"), e)
            false
        }
    }

    val bluetoothDevice: BluetoothDevice?
        get() = getBluetoothGatt(true)?.device

    fun close() {
        close(true)
    }

    internal fun close(remove: Boolean) {
        synchronized(gattManager) {
            if (remove) {
                gattManager.removeGattHandler(this)
            }
            disconnect()
        }
    }

    private fun logPrefix(message: String): String {
        return "$deviceAddressString $message"
    }

    private val isMainThread: Boolean
        get() = handlerMain.looper == Looper.myLooper()

    val isConnectingOrConnectedAndNotDisconnecting: Boolean
        get() = isConnectingOrConnectedAndNotDisconnecting("{public}")

    private fun isConnectingOrConnectedAndNotDisconnecting(callerName: String): Boolean {
        return internalIsConnectingOrConnectedAndNotDisconnecting(callerName, null)
    }

    private fun ignoreIfIsConnectingOrConnectedAndNotDisconnecting(callerName: String): Boolean {
        return internalIsConnectingOrConnectedAndNotDisconnecting(callerName, "ignoring")
    }

    private fun internalIsConnectingOrConnectedAndNotDisconnecting(callerName: String, logSuffixIfTrue: String?): Boolean {
        //Log.e(TAG, "isConnectingOrConnectedAndNotDisconnecting(callerName=" + callerName + ')');
        if (getBluetoothGatt(true) == null) {
            return false
        }
        Log.w(
            TAG,
            logPrefix("$callerName: isConnectingOrConnectedAndNotDisconnecting(...) == true${if (isNullOrEmpty(logSuffixIfTrue)) "" else "; $logSuffixIfTrue"}")
        )
        return true
    }

    fun isDisconnectingOrDisconnected(callerName: String): Boolean {
        return internalIsDisconnectingOrDisconnected(callerName, null)
    }

    fun ignoreIfIsDisconnectingOrDisconnected(callerName: String): Boolean {
        return internalIsDisconnectingOrDisconnected(callerName, "ignoring")
    }

    private fun internalIsDisconnectingOrDisconnected(callerName: String, logSuffixIfTrue: String?): Boolean {
        //Log.e(TAG, "isConnectingOrConnectedAndNotDisconnecting(callerName=" + callerName + ')');
        if (getBluetoothGatt(false) != null) {
            return false
        }
        Log.w(
            TAG,
            logPrefix("$callerName: isDisconnectingOrDisconnected(...) == true${if (isNullOrEmpty(logSuffixIfTrue)) "" else "; $logSuffixIfTrue"}")
        )
        return true
    }

    val isDisconnected: Boolean
        get() = getBluetoothGatt(false) == null

    private fun postDelayed(runnable: Runnable, delayMillis: Long = POST_DELAY_MILLIS.toLong()) {
        handlerMain.postDelayed(runnable, delayMillis)
    }

    private fun getBluetoothGatt(onlyIfConnectingOrConnectedAndNotDisconnecting: Boolean): BluetoothGatt? {
        //Log.e(TAG, "getBluetoothGatt(onlyIfConnectingOrConnectedAndNotDisconnecting=" + onlyIfConnectingOrConnectedAndNotDisconnecting + ')');
        synchronized(gattManager) {
            //Log.e(TAG, "getBluetoothGatt: mBluetoothGatt=" + mBluetoothGatt);
            var gatt = bluetoothGatt
            if (gatt != null) {
                //Log.e(TAG, "getBluetoothGatt: mIsSolicitedDisconnecting=" + mIsSolicitedDisconnecting);
                if (onlyIfConnectingOrConnectedAndNotDisconnecting && isDisconnecting) {
                    gatt = null
                }
            }
            return gatt
        }
    }

    //
    //
    //

    fun addListener(listener: GattHandlerListener) {
        //Log.e(TAG, logPrefix("addListener $listener"))
        listenerManager.attach(listener)
    }

    fun removeListener(listener: GattHandlerListener) {
        //Log.e(TAG, logPrefix("removeListener $listener"))
        listenerManager.detach(listener)
    }

    fun clearListeners() {
        listenerManager.clear()
    }

    private fun timerStart(operation: GattOperation): Long {
        //Log.e(TAG, logPrefix("timerStart(operation=" + operation + ')'));
        val startTimeMillis = System.currentTimeMillis()
        //Log.e(TAG, logPrefix("timerStart: startTimeMillis=" + startTimeMillis));
        startTimes[operation] = startTimeMillis
        return startTimeMillis
    }

    private fun timerElapsed(operation: GattOperation, remove: Boolean): Long {
        //Log.e(TAG, logPrefix("timerElapsed(operation=" + operation + ", remove=" + remove + ')'));
        var elapsedMillis: Long = -1
        val startTimeMillis: Long? = if (remove) {
            startTimes.remove(operation)
        } else {
            startTimes[operation]
        }
        //Log.e(TAG, logPrefix("timerElapsed: startTimeMillis=" + startTimeMillis));
        if (startTimeMillis != null) {
            elapsedMillis = System.currentTimeMillis() - startTimeMillis
        }
        //Log.e(TAG, logPrefix("timerElapsed: elapsedMillis=" + elapsedMillis));
        return elapsedMillis
    }

    //
    //
    //

    private var mConnectAutoConnect = false
    private var mConnectExternalTimeoutMillis: Long = 0
    private var mConnectRunAfterConnect: Runnable? = null
    private var mConnectRunAfterFail: Runnable? = null
    private var mConnectStartTimeMillis: Long = 0

    /**
     * @param autoConnect   boolean
     * @param timeoutMillis long
     * @param runAfterConnect
     * @param runAfterFail
     * @return true if already connecting/connected and *NOT* disconnecting, or the connect request was enqueued,
     * otherwise false
     */
    fun connect(
        autoConnect: Boolean = false,
        timeoutMillis: Long = defaultConnectTimeoutMillis.toLong(),
        runAfterConnect: Runnable? = null,
        runAfterFail: Runnable? = null
    ): Boolean {
        if (!isBluetoothAdapterEnabled("connect")) {
            return false
        }
        if (ignoreIfIsConnectingOrConnectedAndNotDisconnecting("connect")) {
            return true
        }
        mConnectAutoConnect = autoConnect
        mConnectExternalTimeoutMillis = timeoutMillis
        mConnectRunAfterConnect = runAfterConnect
        mConnectRunAfterFail = runAfterFail
        mConnectStartTimeMillis = timerStart(GattOperation.Connect)
        return connectInternal("connect", true, mConnectStartTimeMillis, mConnectAutoConnect, mConnectRunAfterConnect, mConnectRunAfterFail)
    }

    private fun reconnectIfConnecting(): PendingGattOperationInfo? {
        var pendingGattOperationInfo: PendingGattOperationInfo? = pendingGattOperationTimeoutCancel() ?: return null
        when (pendingGattOperationInfo!!.operation) {
            GattOperation.Connect, GattOperation.DiscoverServices -> {
            }
            else -> return null
        }
        val startTimeMillis = pendingGattOperationInfo.startTimeMillis
        val elapsedMillis = System.currentTimeMillis() - startTimeMillis
        val reconnecting = elapsedMillis < mConnectExternalTimeoutMillis
        if (reconnecting) {
            pendingGattOperationInfo = null
            postDelayed(Runnable {
                safeDisconnect("reconnectIfConnecting", bluetoothGatt)
                postDelayed(Runnable {
                    if (!connectInternal(
                            "reconnectIfConnecting",
                            false,
                            mConnectStartTimeMillis,
                            mConnectAutoConnect,
                            mConnectRunAfterConnect,
                            mConnectRunAfterFail
                        )
                    ) {
                        Log.e(TAG, logPrefix("reconnectIfConnecting: failed to request reconnect"))
                    }
                })
            })
        }
        return pendingGattOperationInfo
    }

    private fun connectInternal(
        callerName: String,
        ignoreIfIsConnectingOrConnectedAndNotDisconnecting: Boolean,
        startTimeMillis: Long,
        autoConnect: Boolean,
        runAfterConnect: Runnable?, runAfterFail: Runnable?
    ): Boolean {
        Log.i(
            TAG, logPrefix(
                "connectInternal(callerName=${quote(callerName)}, autoConnect=$autoConnect, runAfterConnect=$runAfterConnect, runAfterFail=$runAfterFail)"
            )
        )
        val callerNameFinal = "connectInternal.$callerName"
        if (!isBluetoothAdapterEnabled(callerNameFinal)) {
            return false
        }
        if (ignoreIfIsConnectingOrConnectedAndNotDisconnecting) {
            if (ignoreIfIsConnectingOrConnectedAndNotDisconnecting(callerNameFinal)) {
                return true
            }
        }
        //final GattOperation operation = GattOperation.Connect;
        val operation = GattOperation.DiscoverServices
        postDelayed(Runnable {
            try {
                Log.v(TAG, logPrefix("+$callerNameFinal.run(): autoConnect=$autoConnect"))
                if (ignoreIfIsConnectingOrConnectedAndNotDisconnecting) {
                    if (ignoreIfIsConnectingOrConnectedAndNotDisconnecting("$callerNameFinal.run")) {
                        return@Runnable
                    }
                }
                pendingGattOperationTimeoutReset(callerNameFinal)
                val bluetoothDevice = bluetoothAdapter!!.getRemoteDevice(deviceAddressString)
                onDeviceConnecting()
                //
                // NOTE: Some Gatt operations, especially connecting, can take "up to" (meaning "over") 30 seconds, per:
                //  http://stackoverflow.com/a/18889509/252308
                //  http://e2e.ti.com/support/wireless_connectivity/f/538/p/281081/985950#985950
                //
                synchronized(gattManager) {
                    //
                    // NOTE:(pv) mBluetoothGatt is only set here and in #onDeviceDisconnected
                    //
                    if (bluetoothGatt != null) {
                        safeDisconnect("$callerNameFinal.run: GattUtils.safeDisconnect(mBluetoothGatt)", bluetoothGatt)
                        Log.v(TAG, logPrefix("$callerNameFinal.run: +mBluetoothGatt.connect()"))
                        bluetoothGatt!!.connect()
                        Log.v(TAG, logPrefix("$callerNameFinal.run: -mBluetoothGatt.connect()"))
                    } else {
                        Log.v(TAG, logPrefix("$callerNameFinal.run: +bluetoothDevice.connectGatt(...)"))
                        val bluetoothGattCompat = BluetoothGattCompat(context)
                        bluetoothGatt = bluetoothGattCompat.connectGatt(bluetoothDevice, autoConnect, backgroundBluetoothGattCallback)
                        Log.v(
                            TAG,
                            logPrefix("$callerNameFinal.run: -bluetoothDevice.connectGatt(...) returned $bluetoothGatt")
                        )
                    }
                }
                if (bluetoothGatt == null) {
                    Log.w(TAG, logPrefix("$callerNameFinal.run: bluetoothDevice.connectGatt(...) failed"))
                    onDeviceDisconnected(null, -1, DisconnectReason.ConnectFailed, false, null)
                } else {
                    // Internally timeout every CONNECT_INTERNAL_TIMEOUT_MILLIS and repeat until mConnectExternalTimeoutMillis is exceeded
                    pendingGattOperationTimeoutSchedule(
                        operation,
                        startTimeMillis,
                        CONNECT_INTERNAL_TIMEOUT_MILLIS.toLong(),
                        runAfterConnect,
                        runAfterFail
                    )
                }
            } finally {
                Log.v(TAG, logPrefix("-$callerNameFinal.run(): autoConnect=$autoConnect"))
            }
        })
        return true
    }

    fun disconnect(timeoutMillis: Long = defaultDisconnectTimeoutMillis.toLong(), runAfterDisconnect: Runnable? = null): Boolean {
        return try {
            Log.i(
                TAG,
                logPrefix("+disconnect(timeoutMillis=$timeoutMillis, runAfterDisconnect=$runAfterDisconnect)")
            )
            synchronized(gattManager) {
                if (bluetoothGatt == null) {
                    Log.w(TAG, logPrefix("disconnect: mBluetoothGatt == null; ignoring"))
                    return false
                }
                if (isDisconnecting) {
                    Log.w(TAG, logPrefix("disconnect: mIsSolicitedDisconnecting == true; ignoring"))
                    return false
                }
                //
                // To be safe, always disconnect from the same thread that the connection was made on
                //
                if (!isMainThread) {
                    Log.v(
                        TAG,
                        logPrefix("disconnect: isMainThread() == false; posting disconnect() to mHandlerMain;")
                    )
                    postDelayed(Runnable { disconnect(timeoutMillis, runAfterDisconnect) })
                    return false
                }
                isDisconnecting = true
                //Log.e(TAG, logPrefix("+mBackgroundPendingOperationSignal.cancel()"));
                pendingGattOperationTimeoutCancel()
                //Log.e(TAG, logPrefix("-mBackgroundPendingOperationSignal.cancel()"));
                if (safeDisconnect("disconnect(timeoutMillis=$timeoutMillis)", bluetoothGatt)) {
                    //
                    // Timeout is needed since BluetoothGatt#disconnect() doesn't always call onConnectionStateChange(..., newState=STATE_DISCONNECTED)
                    //
                    handlerMain.obtainAndSendMessageDelayed(
                        HandlerMainMessages.SolicitedDisconnectInternalTimeout,
                        0,
                        0,
                        runAfterDisconnect,
                        timeoutMillis
                    )
                } else {
                    onDeviceDisconnected(bluetoothGatt, -1, DisconnectReason.SolicitedDisconnect, true, runAfterDisconnect)
                }
            }
            true
        } finally {
            Log.i(
                TAG,
                logPrefix("-disconnect(timeoutMillis=$timeoutMillis, runAfterDisconnect=$runAfterDisconnect)")
            )
        }
    }

    /**
     * Consolidates logic for solicited connect failed, solicited disconnect success, solicited disconnect timeout, and unsolicited disconnect.
     *
     * @param gatt               BluetoothGatt
     * @param status             int
     * @param reason             DisconnectReason
     * @param logStatusAndState  boolean
     * @param runAfterDisconnect Runnable
     */
    private fun onDeviceDisconnected(
        gatt: BluetoothGatt?,
        status: Int,
        reason: DisconnectReason,
        logStatusAndState: Boolean,
        runAfterDisconnect: Runnable?
    ) {
        Log.v(
            TAG, logPrefix(
                "onDeviceDisconnected(gatt, status=$status, reason=$reason, logStatusAndState=$logStatusAndState, runAfterDisconnect=$runAfterDisconnect)"
            )
        )
        synchronized(gattManager) {
            if (bluetoothGatt == null) {
                Log.w(TAG, logPrefix("onDeviceDisconnected: mBluetoothGatt == null; ignoring"))
                return
            }
            // Only set here and in #connect
            bluetoothGatt = null
            val elapsedMillis = timerElapsed(GattOperation.Connect, true).toInt()
            pendingGattOperationTimeoutCancel()
            handlerMain.removeMessages(HandlerMainMessages.SolicitedDisconnectInternalTimeout)
            startTimes.clear()
            if (logStatusAndState) {
                logStatusIfNotSuccess("onDeviceDisconnected", status, null)
            }
            isWaitingForCharacteristicSetNotification = false
            // Only set here and in #disconnect
            isDisconnecting = false
            safeClose("onDeviceDisconnected", gatt)
            postDelayed(Runnable {
                if (bluetoothGatt != null) {
                    Log.w(TAG, logPrefix("onDeviceDisconnected: mBluetoothGatt != null; ignoring"))
                    return@Runnable
                }
                Log.v(TAG, logPrefix("onDeviceDisconnected: +deviceListener(s).onDeviceDisconnected(...)"))
                for (deviceListener in listenerManager.beginTraversing()) {
                    if (deviceListener.onDeviceDisconnected(
                            this@GattHandler,
                            status,
                            reason,
                            elapsedMillis.toLong()
                        )
                    ) {
                        removeListener(deviceListener)
                    }
                }
                listenerManager.endTraversing()
                Log.v(TAG, logPrefix("onDeviceDisconnected: -deviceListener(s).onDeviceDisconnected(...)"))
                runAfterDisconnect?.run()
            })
        }
    }

    /**
     * NOTE: Some status codes can be found at...
     * https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/master/stack/include/gatt_api.h
     * ...not that they are very descriptive or helpful or anything like that! :/
     *
     * @param callerName String
     * @param status     int
     * @param text       String
     */
    private fun logStatusIfNotSuccess(callerName: String, status: Int, text: String?) {
        var message = when (status) {
            BluetoothGatt.GATT_SUCCESS ->
                // ignore
                return
            133 ->
                //
                // https://code.google.com/p/android/issues/detail?id=58381
                // Too many device connections? (hard coded limit of ~4-7ish?)
                // NOTE:(pv) This problem can supposedly be sometimes induced by not calling "gatt.close()" when disconnecting
                //
                "Got the status 133 bug (too many connections?); see https://code.google.com/p/android/issues/detail?id=58381"
            257 ->
                //
                // https://code.google.com/p/android/issues/detail?id=183108
                // NOTE:(pv) This problem can supposedly be sometimes induced by calling "gatt.close()" before "onConnectionStateChange" is called by "gatt.disconnect()"
                //
                "Got the status 257 bug (disconnect()->close()); see https://code.google.com/p/android/issues/detail?id=183108"
            else ->
                "error status=$status"
        }
        if (!isNullOrEmpty(text)) {
            message += " $text"
        }
        Log.e(TAG, logPrefix("$callerName: $message"))
    }

    /**
     * NOTE: Some status codes can be found at...
     * https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/master/stack/include/gatt_api.h
     * ...not that they are very descriptive or helpful or anything like that! :/
     *
     *
     * See [BluetoothGattCallback.onConnectionStateChange]
     */
    private fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        @Suppress("NAME_SHADOWING") var status = status
        val newStateString = bluetoothProfileStateToString(newState)
        Log.v(
            TAG, logPrefix(
                "onConnectionStateChange(gatt, status=$status, newState=$newStateString)"
            )
        )
        /*
        if (true && false) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.e(
                    TAG,
                    logPrefix("onConnectionStateChange: ignoring newState == BluetoothProfile.STATE_CONNECTED to fake connection timeout")
                )
                return
            }
        }
        */
        //final int DEBUG_FAKE_STATUS_ERROR = 257;//....
        //final int DEBUG_FAKE_STATUS_ERROR = 133;
        @Suppress("LocalVariableName") val DEBUG_FAKE_STATUS_ERROR = BluetoothGatt.GATT_SUCCESS
        if (BuildConfig.DEBUG && DEBUG_FAKE_STATUS_ERROR != BluetoothGatt.GATT_SUCCESS) {
            status = DEBUG_FAKE_STATUS_ERROR
            Log.e(TAG, logPrefix("onConnectionStateChange: ***FAKE*** STATUS $status ERROR"))
        } else if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e(TAG, logPrefix("onConnectionStateChange: ***REAL*** STATUS $status ERROR"))
        }
        val finalStatus = status
        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            /*
            // Sometimes, when a notification/indication is received after the device got
            // disconnected, the Android calls onConnectionStateChanged again, with state
            // STATE_CONNECTED.
            // See: https://github.com/NordicSemiconductor/Android-BLE-Library/issues/43
            if (mBluetoothDevice == null) {
                Log.e(TAG, "Device received notification after disconnection.");
                log(Log.DEBUG, "gatt.close()");
                try {
                    gatt.close();
                } catch (final Throwable t) {
                    // ignore
                }
                return;
            }
            */
            onDeviceConnected()
            val bonded = gatt.device.bondState == BluetoothDevice.BOND_BONDED
            val serviceDiscoveryDelay = getServiceDiscoveryDelay(bonded)
            postDelayed(Runnable {
                if (bluetoothGatt == null) {
                    // Ensure that we will not try to discover services for a lost connection.
                    return@Runnable
                }
                timerStart(GattOperation.DiscoverServices)
                if (!bluetoothGatt!!.discoverServices()) {
                    Log.e(
                        TAG,
                        logPrefix("onConnectionStateChange: gatt.discoverServices() failed; disconnecting...")
                    )
                    onDeviceDisconnected(bluetoothGatt, finalStatus, DisconnectReason.UnsolicitedDisconnect, false, null)
                }
            }, serviceDiscoveryDelay.toLong())
            return
        }
        val reconnecting = reconnectIfConnecting() == null
        if (newState != BluetoothProfile.STATE_DISCONNECTED) {
            Log.e(
                TAG, logPrefix(
                    "onConnectionStateChange: UNEXPECTED newState=$newStateString, status=$status; ${if (reconnecting) "reconnecting" else "disconnecting"}..."
                )
            )
        }
        if (reconnecting) {
            return
        }
        synchronized(gattManager) {
            val reason = if (isDisconnecting) DisconnectReason.SolicitedDisconnect else DisconnectReason.UnsolicitedDisconnect
            onDeviceDisconnected(gatt, status, reason, true, null)
        }
    }

    private fun onDeviceOperationTimeout(
        gattOperation: GattOperation,
        timeoutMillis: Long,
        elapsedMillis: Long
    ) {
        postDelayed(Runnable {
            var disconnect = true
            for (deviceListener in listenerManager.beginTraversing()) {
                if (deviceListener.onDeviceOperationTimeout(
                        this@GattHandler,
                        gattOperation,
                        timeoutMillis,
                        elapsedMillis
                    )
                ) {
                    disconnect = false
                }
            }
            listenerManager.endTraversing()
            if (disconnect) {
                disconnect()
            }
        })
    }

    private fun onDeviceConnecting() {
        postDelayed(Runnable {
            var disconnect = false
            for (deviceListener in listenerManager.beginTraversing()) {
                disconnect = disconnect or deviceListener.onDeviceConnecting(this@GattHandler)
            }
            listenerManager.endTraversing()
            Log.v(TAG, logPrefix("onDeviceConnecting: disconnect=$disconnect"))
            if (disconnect) {
                disconnect()
            }
        })
    }

    private fun onDeviceConnected() {
        val elapsedMillis = timerElapsed(GattOperation.Connect, false)
        postDelayed(Runnable {
            var disconnect = false
            for (deviceListener in listenerManager.beginTraversing()) {
                disconnect = disconnect or deviceListener.onDeviceConnected(
                    this@GattHandler,
                    elapsedMillis
                )
            }
            listenerManager.endTraversing()
            Log.v(TAG, logPrefix("onDeviceConnected: disconnect=$disconnect"))
            if (disconnect) {
                disconnect()
            }
        })
    }

    private fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        Log.v(TAG, logPrefix("onServicesDiscovered(gatt, status=$status)"))
        val elapsedMillis = timerElapsed(GattOperation.DiscoverServices, true).toInt()
        logStatusIfNotSuccess("onServicesDiscovered", status, null)
        val success = status == BluetoothGatt.GATT_SUCCESS
        val services = if (!success) {
            null
        } else {
            gatt.services
        }
        onDeviceServicesDiscovered(services, success, elapsedMillis.toLong())
    }

    private val lockLogServicesAndCharacteristics = Any()

    private fun logServicesAndCharacteristics(
        callerName: String,
        gatt: BluetoothGatt?,
        services: List<BluetoothGattService>? = null
    ) {
        @Suppress("NAME_SHADOWING") var services = services
        if (!DEBUG_LOG_SERVICES_AND_CHARACTERISTICS) {
            return
        }
        if (gatt == null) {
            return
        }
        synchronized(lockLogServicesAndCharacteristics) {
            if (services == null) {
                services = gatt.services
            }
            //
            // Used to test BluetoothGatt.getService(uuidKnownToExistOnDevice) sometimes returning null
            //
            // https://stackoverflow.com/questions/41756294/android-bluetoothgatt-getservicesxyz-in-onservicesdiscovered-returns-null-for
            // https://stackoverflow.com/questions/56250434/bluetoothgatt-getserviceuuid-returns-null-when-i-know-the-device-offers-said
            //
            var service: BluetoothGattService
            var uuid: UUID
            var serviceGet: BluetoothGattService?
            var characteristics: List<BluetoothGattCharacteristic>
            var characteristic: BluetoothGattCharacteristic
            var characteristicGet: BluetoothGattCharacteristic?
            var i = 0
            val servicesSize = services!!.size
            while (i < servicesSize) {
                service = services!![i]
                // @formatter:off
                Log.v(TAG, logPrefix("$callerName:                    services[${formatNumber(i.toLong(), 2)}]=${toString(service)}"))
                uuid = service.uuid
                Log.v(TAG, logPrefix("$callerName:          services[${formatNumber(i.toLong(), 2)}].getUuid()=$uuid"))
                serviceGet = gatt.getService(uuid)
                Log.v(TAG, logPrefix("$callerName:           gatt.getService(uuid)=${toString(serviceGet)}"))
                characteristics = service.characteristics
                var  j = 0
                val  characteristicsSize = characteristics.size
                while (j < characteristicsSize){
                    characteristic = characteristics[j]
                    Log.v(TAG, logPrefix("$callerName:             characteristics[${formatNumber(j.toLong(), 2)}]=${toString(characteristic)}"))
                    uuid = characteristic.uuid
                    Log.v(TAG, logPrefix("$callerName:   characteristics[${formatNumber(j.toLong(), 2)}].getUuid()=$uuid"))
                    characteristicGet = service.getCharacteristic(uuid)
                    Log.v(TAG, logPrefix("$callerName: service.getCharacteristic(uuid)=${toString(characteristicGet)}"))
                    j++
                }
                Log.v(TAG, logPrefix("$callerName: ------------------------------------------"))
                // @formatter:on
                i++
            }
        }
    }

    private fun onDeviceServicesDiscovered(
        services: List<BluetoothGattService>?,
        success: Boolean,
        elapsedMillis: Long
    ) {
        if (!pendingGattOperationTimeoutSignal()) {
            Log.w(TAG, logPrefix("onDeviceServicesDiscovered: pendingGattOperationTimeoutSignal() == false; ignoring"))
            return
        }
        logServicesAndCharacteristics("onDeviceServicesDiscovered", bluetoothGatt, services)
        postDelayed(Runnable {
            var disconnect = false
            for (deviceListener in listenerManager.beginTraversing()) {
                disconnect = disconnect or deviceListener.onDeviceServicesDiscovered(
                    this@GattHandler,
                    services,
                    success,
                    elapsedMillis
                )
            }
            listenerManager.endTraversing()
            Log.v(TAG, logPrefix("onDeviceServicesDiscovered: success=$success, disconnect=$disconnect"))
            if (!success || disconnect) {
                disconnect()
            }
        })
    }

    //
    //
    //

    fun characteristicRead(
        serviceUuid: UUID, characteristicUuid: UUID,
        timeoutMillis: Long = defaultOperationTimeoutMillis.toLong(),
        runAfterSuccess: Runnable? = null, runAfterFail: Runnable? = null
    ): Boolean {
        Log.i(
            TAG, logPrefix(
                "characteristicRead(serviceUuid=$serviceUuid, characteristicUuid=$characteristicUuid, timeoutMillis=$timeoutMillis, runAfterSuccess=$runAfterSuccess, runAfterFail=$runAfterFail)"
            )
        )
        if (!isBluetoothAdapterEnabled("characteristicRead")) {
            return false
        }
        if (ignoreIfIsDisconnectingOrDisconnected("characteristicRead")) {
            return false
        }
        val operation = GattOperation.CharacteristicRead
        val startTimeMillis = timerStart(operation)
        postDelayed(Runnable {
            try {
                Log.v(
                    TAG, logPrefix(
                        "+characteristicRead.run(): serviceUuid=$serviceUuid, characteristicUuid=$characteristicUuid, timeoutMillis=$timeoutMillis"
                    )
                )
                val gatt = pendingGattOperationTimeoutReset("characteristicRead")
                if (gatt == null) {
                    onDeviceCharacteristicRead(serviceUuid, characteristicUuid, false)
                    return@Runnable
                }
                val service = gatt.getService(serviceUuid)
                if (service == null) {
                    Log.e(
                        TAG, logPrefix(
                            "characteristicRead.run: gatt.getService($serviceUuid) failed"
                        )
                    )
                    onDeviceCharacteristicRead(serviceUuid, characteristicUuid, false)
                    return@Runnable
                }
                val characteristic = service.getCharacteristic(characteristicUuid)
                if (characteristic == null) {
                    Log.e(
                        TAG, logPrefix(
                            "characteristicRead.run: service.getCharacteristic($characteristicUuid) failed"
                        )
                    )
                    onDeviceCharacteristicRead(serviceUuid, characteristicUuid, false)
                    return@Runnable
                }
                if (!gatt.readCharacteristic(characteristic)) {
                    Log.e(
                        TAG, logPrefix(
                            "characteristicRead.run: gatt.characteristicRead(...) failed for characteristic $characteristicUuid"
                        )
                    )
                    onDeviceCharacteristicRead(serviceUuid, characteristicUuid, false)
                    return@Runnable
                }
                pendingGattOperationTimeoutSchedule(operation, startTimeMillis, timeoutMillis, runAfterSuccess, runAfterFail)
            } finally {
                Log.v(
                    TAG, logPrefix(
                        "-characteristicRead.run(): serviceUuid=$serviceUuid, characteristicUuid=$characteristicUuid, timeoutMillis=$timeoutMillis"
                    )
                )
            }
        })
        return true
    }

    private fun onCharacteristicRead(
        @Suppress("UNUSED_PARAMETER") gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        val characteristicUuid = characteristic.uuid
        Log.v(TAG, logPrefix("onCharacteristicRead(gatt, characteristic=$characteristicUuid, status=$status)"))
        logStatusIfNotSuccess("onCharacteristicRead", status, "for characteristic $characteristicUuid")
        val success = status == BluetoothGatt.GATT_SUCCESS
        onDeviceCharacteristicRead(characteristic, success)
    }

    private fun onDeviceCharacteristicRead(
        serviceUuid: UUID, characteristicUuid: UUID,
        success: Boolean
    ) {
        val characteristic = createBluetoothGattCharacteristic(serviceUuid, characteristicUuid)
        onDeviceCharacteristicRead(characteristic, success)
    }

    private fun onDeviceCharacteristicRead(
        characteristic: BluetoothGattCharacteristic,
        success: Boolean
    ) {
        if (!pendingGattOperationTimeoutSignal()) {
            Log.w(TAG, logPrefix("onDeviceCharacteristicRead: pendingGattOperationTimeoutSignal() == false; ignoring"))
            return
        }
        val elapsedMillis = timerElapsed(GattOperation.CharacteristicRead, true)
        postDelayed(Runnable {
            var disconnect = false
            for (deviceListener in listenerManager.beginTraversing()) {
                disconnect = disconnect or deviceListener.onDeviceCharacteristicRead(
                    this@GattHandler,
                    characteristic,
                    success,
                    elapsedMillis
                )
            }
            listenerManager.endTraversing()
            Log.v(TAG, logPrefix("onDeviceCharacteristicRead: success=$success, disconnect=$disconnect"))
            if (!success || disconnect) {
                disconnect()
            }
        })
    }

    //
    //
    //

    enum class CharacteristicWriteType {
        /**
         * Results in writing a [BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT]
         */
        DefaultWithResponse,
        /**
         * Results in writing a [BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE]
         */
        WithoutResponse,
        /**
         * Results in writing a [BluetoothGattCharacteristic.WRITE_TYPE_SIGNED]
         */
        Signed
    }

    /**
     * @param serviceUuid             UUID
     * @param characteristicUuid      UUID
     * @param value                   String
     * @param characteristicWriteType null to ignore
     */
    fun characteristicWrite(
        serviceUuid: UUID, characteristicUuid: UUID,
        value: String?,
        characteristicWriteType: CharacteristicWriteType? = null,
        timeoutMillis: Long = defaultOperationTimeoutMillis.toLong(),
        runAfterSuccess: Runnable? = null, runAfterFail: Runnable? = null
    ): Boolean {
        return characteristicWrite(
            serviceUuid,
            characteristicUuid,
            toBytes(value),
            characteristicWriteType,
            timeoutMillis,
            runAfterSuccess,
            runAfterFail
        )
    }

    /**
     * @param serviceUuid             UUID
     * @param characteristicUuid      UUID
     * @param value                   int
     * @param formatType              One of BluetoothGattCharacteristic.FORMAT_*
     * @param offset                  int
     * @param characteristicWriteType null to ignore
     */
    fun characteristicWrite(
        serviceUuid: UUID, characteristicUuid: UUID,
        value: Int, formatType: Int, offset: Int,
        characteristicWriteType: CharacteristicWriteType? = null,
        timeoutMillis: Long = defaultOperationTimeoutMillis.toLong(),
        runAfterSuccess: Runnable? = null, runAfterFail: Runnable? = null
    ): Boolean {
        return characteristicWrite(
            serviceUuid,
            characteristicUuid,
            toBytes(value, formatType, offset),
            characteristicWriteType,
            timeoutMillis,
            runAfterSuccess,
            runAfterFail
        )
    }

    /**
     * @param serviceUuid             UUID
     * @param characteristicUuid      UUID
     * @param mantissa                int
     * @param exponent                int
     * @param formatType              One of BluetoothGattCharacteristic.FORMAT_*
     * @param offset                  int
     * @param characteristicWriteType null to ignore
     */
    fun characteristicWrite(
        serviceUuid: UUID, characteristicUuid: UUID,
        mantissa: Int, exponent: Int, formatType: Int, offset: Int,
        characteristicWriteType: CharacteristicWriteType? = null,
        timeoutMillis: Long = defaultOperationTimeoutMillis.toLong(),
        runAfterSuccess: Runnable? = null, runAfterFail: Runnable? = null
    ): Boolean {
        return characteristicWrite(
            serviceUuid,
            characteristicUuid,
            toBytes(mantissa, exponent, formatType, offset),
            characteristicWriteType,
            timeoutMillis,
            runAfterSuccess,
            runAfterFail
        )
    }

    /**
     * @param serviceUuid             UUID
     * @param characteristicUuid      UUID
     * @param value                   byte[]
     * @param characteristicWriteType null to ignore
     * @param timeoutMillis           long
     */
    fun characteristicWrite(
        serviceUuid: UUID, characteristicUuid: UUID,
        value: ByteArray,
        characteristicWriteType: CharacteristicWriteType? = null,
        timeoutMillis: Long = defaultOperationTimeoutMillis.toLong(),
        runAfterSuccess: Runnable? = null, runAfterFail: Runnable? = null
    ): Boolean {
        Log.i(
            TAG, logPrefix(
                "characteristicWrite(serviceUuid=$serviceUuid, characteristicUuid=$characteristicUuid, value=${value.contentToString()}, characteristicWriteType=$characteristicWriteType, timeoutMillis=$timeoutMillis, runAfterSuccess=$runAfterSuccess, runAfterFail=$runAfterFail)"
            )
        )
        if (!isBluetoothAdapterEnabled("characteristicWrite")) {
            return false
        }
        if (ignoreIfIsDisconnectingOrDisconnected("characteristicWrite")) {
            return false
        }
        val operation = GattOperation.CharacteristicWrite
        val startTimeMillis = timerStart(operation)
        postDelayed(Runnable {
            try {
                Log.v(
                    TAG, logPrefix(
                        "+characteristicWrite.run(): serviceUuid=$serviceUuid, characteristicUuid=$characteristicUuid, value=${value.contentToString()}, characteristicWriteType=$characteristicWriteType, timeoutMillis=$timeoutMillis"
                    )
                )
                val gatt = pendingGattOperationTimeoutReset("characteristicWrite")
                if (gatt == null) {
                    onDeviceCharacteristicWrite(serviceUuid, characteristicUuid, false)
                    return@Runnable
                }
                logServicesAndCharacteristics("characteristicWrite", gatt)
                val service = gatt.getService(serviceUuid)
                if (service == null) {
                    Log.e(TAG, logPrefix("characteristicWrite.run: gatt.getService($serviceUuid) failed"))
                    onDeviceCharacteristicWrite(serviceUuid, characteristicUuid, false)
                    return@Runnable
                }
                val characteristic = service.getCharacteristic(characteristicUuid)
                if (characteristic == null) {
                    Log.e(TAG, logPrefix("characteristicWrite.run: service.getCharacteristic($characteristicUuid) failed"))
                    onDeviceCharacteristicWrite(serviceUuid, characteristicUuid, false)
                    return@Runnable
                }
                if (characteristicWriteType != null) {
                    characteristic.writeType = when (characteristicWriteType) {
                        CharacteristicWriteType.WithoutResponse -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        CharacteristicWriteType.Signed -> BluetoothGattCharacteristic.WRITE_TYPE_SIGNED
                        CharacteristicWriteType.DefaultWithResponse -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    }
                }
                if (!characteristic.setValue(value)) {
                    Log.e(
                        TAG,
                        logPrefix("characteristicWrite: characteristic.setValue(${value.contentToString()} failed for characteristic $characteristicUuid")
                    )
                    onDeviceCharacteristicWrite(serviceUuid, characteristicUuid, false)
                    return@Runnable
                }
                if (!gatt.writeCharacteristic(characteristic)) {
                    Log.e(TAG, logPrefix("characteristicWrite.run: gatt.characteristicWrite(...) failed for characteristic $characteristicUuid"))
                    onDeviceCharacteristicWrite(serviceUuid, characteristicUuid, false)
                    return@Runnable
                }
                pendingGattOperationTimeoutSchedule(operation, startTimeMillis, timeoutMillis, runAfterSuccess, runAfterFail)
            } finally {
                Log.v(
                    TAG, logPrefix(
                        "-characteristicWrite.run(): serviceUuid=$serviceUuid, characteristicUuid=$characteristicUuid, value=${value.contentToString()}, characteristicWriteType=$characteristicWriteType, timeoutMillis=$timeoutMillis"
                    )
                )
            }
        })
        return true
    }

    private fun onCharacteristicWrite(@Suppress("UNUSED_PARAMETER") gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        val characteristicUuid = characteristic.uuid
        Log.v(TAG, logPrefix("onCharacteristicWrite(gatt, characteristic=$characteristicUuid, status=$status)"))
        logStatusIfNotSuccess("onCharacteristicWrite", status, "for characteristic $characteristicUuid")
        val success = status == BluetoothGatt.GATT_SUCCESS
        onDeviceCharacteristicWrite(characteristic, success)
    }

    private fun onDeviceCharacteristicWrite(serviceUuid: UUID, characteristicUuid: UUID, success: Boolean) {
        val characteristic = createBluetoothGattCharacteristic(serviceUuid, characteristicUuid)
        onDeviceCharacteristicWrite(characteristic, success)
    }

    private fun onDeviceCharacteristicWrite(characteristic: BluetoothGattCharacteristic, success: Boolean) {
        if (!pendingGattOperationTimeoutSignal()) {
            Log.w(TAG, logPrefix("onDeviceCharacteristicWrite: pendingGattOperationTimeoutSignal() == false; ignoring"))
            return
        }
        val elapsedMillis = timerElapsed(GattOperation.CharacteristicWrite, true)
        postDelayed(Runnable {
            var disconnect = false
            for (deviceListener in listenerManager.beginTraversing()) {
                disconnect = disconnect or deviceListener.onDeviceCharacteristicWrite(
                    this@GattHandler,
                    characteristic,
                    success,
                    elapsedMillis
                )
            }
            listenerManager.endTraversing()
            Log.v(TAG, logPrefix("onDeviceCharacteristicWrite: success=$success, disconnect=$disconnect"))
            if (!success || disconnect) {
                disconnect()
            }
        })
    }

    //
    //
    //

    enum class CharacteristicNotificationDescriptorType {
        /**
         * Results in writing a [BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE]
         */
        Disable,
        /**
         * Results in writing a [BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE]
         */
        EnableWithoutResponse,
        /**
         * Results in writing a [BluetoothGattDescriptor.ENABLE_INDICATION_VALUE]
         */
        EnableWithResponse
    }

    fun characteristicSetNotification(
        serviceUuid: UUID, characteristicUuid: UUID,
        characteristicNotificationDescriptorType: CharacteristicNotificationDescriptorType,
        setDescriptorClientCharacteristicConfig: Boolean,
        timeoutMillis: Long = defaultOperationTimeoutMillis.toLong(),
        runAfterSuccess: Runnable? = null, runAfterFail: Runnable? = null
    ): Boolean {
        Log.i(
            TAG, logPrefix(
                "characteristicSetNotification(serviceUuid=$serviceUuid, characteristicUuid=$characteristicUuid, characteristicNotificationDescriptorType=$characteristicNotificationDescriptorType, setDescriptorClientCharacteristicConfig=$setDescriptorClientCharacteristicConfig, timeoutMillis=$timeoutMillis, runAfterSuccess=$runAfterSuccess, runAfterFail=$runAfterFail)"
            )
        )
        if (!isBluetoothAdapterEnabled("characteristicSetNotification")) {
            return false
        }
        if (ignoreIfIsDisconnectingOrDisconnected("characteristicSetNotification")) {
            return false
        }
        val operation = GattOperation.CharacteristicSetNotification
        val startTimeMillis = timerStart(operation)
        postDelayed(Runnable {
            try {
                Log.v(
                    TAG, logPrefix(
                        "+characteristicSetNotification.run(): serviceUuid=$serviceUuid, characteristicUuid=$characteristicUuid, characteristicNotificationDescriptorType=$characteristicNotificationDescriptorType, setDescriptorClientCharacteristicConfig=$setDescriptorClientCharacteristicConfig, timeoutMillis=$timeoutMillis"
                    )
                )
                val gatt = pendingGattOperationTimeoutReset("characteristicSetNotification")
                if (gatt == null) {
                    onDeviceCharacteristicSetNotification(serviceUuid, characteristicUuid, false)
                    return@Runnable
                }
                val service = gatt.getService(serviceUuid)
                if (service == null) {
                    Log.e(TAG, logPrefix("characteristicSetNotification.run: gatt.getService($serviceUuid) failed"))
                    onDeviceCharacteristicSetNotification(serviceUuid, characteristicUuid, false)
                    return@Runnable
                }
                val characteristic = service.getCharacteristic(characteristicUuid)
                if (characteristic == null) {
                    Log.e(TAG, logPrefix("characteristicSetNotification.run: service.getCharacteristic($characteristicUuid) failed"))
                    onDeviceCharacteristicSetNotification(serviceUuid, characteristicUuid, false)
                    return@Runnable
                }
                val enable = characteristicNotificationDescriptorType != CharacteristicNotificationDescriptorType.Disable
                if (!gatt.setCharacteristicNotification(characteristic, enable)) {
                    Log.e(
                        TAG, logPrefix(
                            "characteristicSetNotification.run: mGattConnectingOrConnected.characteristicSetNotification(..., enable=$enable) failed for characteristic $characteristicUuid"
                        )
                    )
                    onDeviceCharacteristicSetNotification(serviceUuid, characteristicUuid, false)
                    return@Runnable
                }
                if (!setDescriptorClientCharacteristicConfig) {
                    //
                    // Success
                    //
                    onDeviceCharacteristicSetNotification(serviceUuid, characteristicUuid, true)
                    return@Runnable
                }
                val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                if (descriptor == null) {
                    Log.e(
                        TAG, logPrefix(
                            "characteristicSetNotification.run: characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG) failed for characteristic $characteristicUuid"
                        )
                    )
                    onDeviceCharacteristicSetNotification(serviceUuid, characteristicUuid, false)
                    return@Runnable
                }
                val descriptorValue = when (characteristicNotificationDescriptorType) {
                    CharacteristicNotificationDescriptorType.EnableWithoutResponse -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    CharacteristicNotificationDescriptorType.EnableWithResponse -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    CharacteristicNotificationDescriptorType.Disable -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }
                if (!descriptor.setValue(descriptorValue)) {
                    Log.e(
                        TAG, logPrefix(
                            "characteristicSetNotification.run: descriptor.setValue(${descriptorValue!!.contentToString()}) failed for descriptor CLIENT_CHARACTERISTIC_CONFIG for characteristic $characteristicUuid"
                        )
                    )
                    onDeviceCharacteristicSetNotification(serviceUuid, characteristicUuid, false)
                    return@Runnable
                }
                if (!gatt.writeDescriptor(descriptor)) {
                    Log.e(
                        TAG, logPrefix(
                            "characteristicSetNotification.run: mGattConnectingOrConnected.writeDescriptor(...) failed descriptor CLIENT_CHARACTERISTIC_CONFIG"
                        )
                    )
                    onDeviceCharacteristicSetNotification(serviceUuid, characteristicUuid, false)
                    return@Runnable
                }
                isWaitingForCharacteristicSetNotification = true
                pendingGattOperationTimeoutSchedule(operation, startTimeMillis, timeoutMillis, runAfterSuccess, runAfterFail)
            } finally {
                Log.v(
                    TAG, logPrefix(
                        "-characteristicSetNotification.run(): serviceUuid=$serviceUuid, characteristicUuid=$characteristicUuid, characteristicNotificationDescriptorType=$characteristicNotificationDescriptorType, setDescriptorClientCharacteristicConfig=$setDescriptorClientCharacteristicConfig, timeoutMillis=$timeoutMillis"
                    )
                )
            }
        })
        return true
    }

    private var isWaitingForCharacteristicSetNotification = false

    private fun onDescriptorWrite(
        @Suppress("UNUSED_PARAMETER") gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor, status: Int
    ) {
        if (!isWaitingForCharacteristicSetNotification) {
            //
            // ignore
            //
            return
        }
        isWaitingForCharacteristicSetNotification = false
        Log.v(TAG, logPrefix("onDescriptorWrite(gatt, descriptor=CLIENT_CHARACTERISTIC_CONFIG, status=$status)"))
        val characteristic = descriptor.characteristic
        logStatusIfNotSuccess("onDescriptorWrite", status, "for descriptor CLIENT_CHARACTERISTIC_CONFIG for characteristic ${characteristic.uuid}")
        val success = status == BluetoothGatt.GATT_SUCCESS
        onDeviceCharacteristicSetNotification(characteristic, success)
    }

    private fun onDeviceCharacteristicSetNotification(serviceUuid: UUID, characteristicUuid: UUID, success: Boolean) {
        val characteristic = createBluetoothGattCharacteristic(serviceUuid, characteristicUuid)
        onDeviceCharacteristicSetNotification(characteristic, success)
    }

    private fun onDeviceCharacteristicSetNotification(characteristic: BluetoothGattCharacteristic, success: Boolean) {
        if (!pendingGattOperationTimeoutSignal()) {
            Log.w(TAG, logPrefix("onDeviceCharacteristicSetNotification: pendingGattOperationTimeoutSignal() == false; ignoring"))
            return
        }
        val elapsedMillis = timerElapsed(GattOperation.CharacteristicSetNotification, true)
        postDelayed(Runnable {
            var disconnect = false
            for (deviceListener in listenerManager.beginTraversing()) {
                disconnect = disconnect or deviceListener.onDeviceCharacteristicSetNotification(
                    this@GattHandler,
                    characteristic,
                    success,
                    elapsedMillis
                )
            }
            listenerManager.endTraversing()
            Log.v(TAG, logPrefix("onDeviceCharacteristicSetNotification: success=$success, disconnect=$disconnect"))
            if (!success || disconnect) {
                disconnect()
            }
        })
    }

    private fun onCharacteristicChanged(@Suppress("UNUSED_PARAMETER") gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (VERBOSE_LOG_CHARACTERISTIC_CHANGE) {
            Log.v(TAG, logPrefix("onCharacteristicChanged: characteristic=${characteristic.uuid}"))
        }
        //
        // Handle the case where disconnect has been called, but the OS has queued up lots of characteristic changes
        //
        if (ignoreIfIsDisconnectingOrDisconnected("onCharacteristicChanged")) {
            return
        }
        //
        // NOTE:(pv) This method may stream LOTS of data.
        // To avoid excessive memory allocations, this method intentionally deviates from the other methods' uses of
        // "mHandler.postDelayed(new Runnable() { ... }, DEFAULT_POST_DELAY_MILLIS)"
        //
        handlerMain.obtainAndSendMessage(HandlerMainMessages.onCharacteristicChanged, characteristic)
    }

    //
    //
    //

    private object HandlerMainMessages {
        /**
         * msg.arg1: ?
         * msg.arg2: ?
         * msg.obj: ?
         */
        const val OperationTimeout = 1

        /**
         * msg.arg1: ?
         * msg.arg2: ?
         * msg.obj: runAfterDisconnect
         */
        const val SolicitedDisconnectInternalTimeout = 2

        /**
         * msg.arg1: ?
         * msg.arg2: ?
         * msg.obj: BluetoothGattCharacteristic
         */
        const val onCharacteristicChanged = 3
    }

    private fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            HandlerMainMessages.OperationTimeout -> {
                Log.v(TAG, logPrefix("handleMessage: OperationTimeout"))
                pendingGattOperationTimeout(msg)
            }
            HandlerMainMessages.SolicitedDisconnectInternalTimeout -> {
                Log.v(TAG, logPrefix("handleMessage: SolicitedDisconnectInternalTimeout"))
                val runAfterDisconnect = msg.obj as Runnable
                onDeviceDisconnected(bluetoothGatt, -1, DisconnectReason.SolicitedDisconnectTimeout, false, runAfterDisconnect)
            }
            HandlerMainMessages.onCharacteristicChanged -> {
                if (ignoreIfIsDisconnectingOrDisconnected("handleMessage: onCharacteristicChanged")) {
                    return false
                }
                val characteristic = msg.obj as BluetoothGattCharacteristic
                if (VERBOSE_LOG_CHARACTERISTIC_CHANGE) {
                    Log.v(TAG, logPrefix("handleMessage: onCharacteristicChanged characteristic=${characteristic.uuid}"))
                }
                var disconnect = false
                for (deviceListener in listenerManager.beginTraversing()) {
                    disconnect = disconnect or deviceListener.onDeviceCharacteristicChanged(this@GattHandler, characteristic)
                }
                listenerManager.endTraversing()
                Log.v(TAG, logPrefix("handleMessage: onCharacteristicChanged: disconnect=$disconnect"))
                if (disconnect) {
                    disconnect()
                }
            }
        }
        return false
    }

    //
    //
    //

    fun readRemoteRssi(
        timeoutMillis: Long = defaultOperationTimeoutMillis.toLong(),
        runAfterSuccess: Runnable? = null,
        runAfterFail: Runnable? = null
    ): Boolean {
        Log.i(TAG, logPrefix("+readRemoteRssi(timeoutMillis=$timeoutMillis, runAfterSuccess=$runAfterSuccess)"))
        if (!isBluetoothAdapterEnabled("readRemoteRssi")) {
            return false
        }
        if (ignoreIfIsDisconnectingOrDisconnected("readRemoteRssi")) {
            return false
        }
        val operation = GattOperation.ReadRemoteRssi
        val startTimeMillis = timerStart(operation)
        postDelayed(Runnable {
            try {
                Log.v(TAG, logPrefix("+readRemoteRssi.run(): timeoutMillis=$timeoutMillis)"))
                val gatt = pendingGattOperationTimeoutReset("readRemoteRssi")
                if (gatt == null) {
                    onDeviceReadRemoteRssi(-1, false)
                    return@Runnable
                }
                if (!gatt.readRemoteRssi()) {
                    Log.e(TAG, logPrefix("readRemoteRssi.run: gatt.readRemoteRssi() failed"))
                    onDeviceReadRemoteRssi(-1, false)
                    return@Runnable
                }
                pendingGattOperationTimeoutSchedule(operation, startTimeMillis, timeoutMillis, runAfterSuccess, runAfterFail)
            } finally {
                Log.v(TAG, logPrefix("-readRemoteRssi.run(): timeoutMillis=$timeoutMillis)"))
            }
        })
        return true
    }

    private fun onReadRemoteRssi(@Suppress("UNUSED_PARAMETER") gatt: BluetoothGatt, rssi: Int, status: Int) {
        Log.v(TAG, logPrefix("onReadRemoteRssi(gatt, rssi=$rssi, status=$status)"))
        logStatusIfNotSuccess("onReadRemoteRssi", status, ", rssi=$rssi")
        val success = status == BluetoothGatt.GATT_SUCCESS
        onDeviceReadRemoteRssi(rssi, success)
    }

    private fun onDeviceReadRemoteRssi(rssi: Int, success: Boolean) {
        if (!pendingGattOperationTimeoutSignal()) {
            Log.w(TAG, logPrefix("onDeviceReadRemoteRssi: pendingGattOperationTimeoutSignal() == false; ignoring"))
            return
        }
        val elapsedMillis = timerElapsed(GattOperation.ReadRemoteRssi, true)
        postDelayed(Runnable {
            var disconnect = false
            for (deviceListener in listenerManager.beginTraversing()) {
                disconnect = disconnect or deviceListener.onDeviceReadRemoteRssi(
                    this@GattHandler,
                    rssi,
                    success,
                    elapsedMillis
                )
            }
            listenerManager.endTraversing()
            Log.v(TAG, logPrefix("onDeviceReadRemoteRssi: success=$success, disconnect=$disconnect"))
            if (!success || disconnect) {
                disconnect()
            }
        })
    }

    //
    //
    //

    private class PendingGattOperationInfo(
        val operation: GattOperation,
        val startTimeMillis: Long,
        val timeoutMillis: Long,
        val runAfterSuccess: Runnable?,
        val runAfterFail: Runnable?
    )

    private var pendingGattOperationInfo: PendingGattOperationInfo? = null

    private fun pendingGattOperationTimeoutSchedule(
        operation: GattOperation,
        startTimeMillis: Long,
        timeoutMillis: Long,
        runAfterSuccess: Runnable?,
        runAfterFail: Runnable?
    ) {
        if (VERBOSE_LOG_PENDING_OPERATION_TIMEOUT) {
            Log.e(
                TAG, logPrefix(
                    "pendingGattOperationTimeoutSchedule(operation=$operation, startTimeMillis=$startTimeMillis, timeoutMillis=$timeoutMillis, runAfterSuccess=$runAfterSuccess, runAfterFail=$runAfterFail)"
                )
            )
        }
        pendingGattOperationInfo = PendingGattOperationInfo(operation, startTimeMillis, timeoutMillis, runAfterSuccess, runAfterFail)
        handlerMain.sendEmptyMessageDelayed(HandlerMainMessages.OperationTimeout, timeoutMillis)
    }

    private fun pendingGattOperationTimeoutCancel(): PendingGattOperationInfo? {
        if (VERBOSE_LOG_PENDING_OPERATION_TIMEOUT) {
            Log.e(TAG, logPrefix("pendingGattOperationTimeoutCancel()"))
        }
        handlerMain.removeMessages(HandlerMainMessages.OperationTimeout)
        val pendingGattOperationInfo = this.pendingGattOperationInfo
        this.pendingGattOperationInfo = null
        return pendingGattOperationInfo
    }

    private fun pendingGattOperationTimeoutReset(callerName: String): BluetoothGatt? {
        if (VERBOSE_LOG_PENDING_OPERATION_TIMEOUT) {
            Log.e(TAG, logPrefix("pendingGattOperationTimeoutReset(callerName=${quote(callerName)})"))
        }
        pendingGattOperationTimeoutCancel()
        @Suppress("UnnecessaryVariable") val gatt = getBluetoothGatt(true)
        //if (gatt == null) {
        //    Log.w(TAG, logPrefix(callerName + " pendingGattOperationTimeoutReset: getBluetoothGatt(true) == null; ignoring"));
        //}
        return gatt
    }

    private fun pendingGattOperationTimeoutSignal(): Boolean {
        if (VERBOSE_LOG_PENDING_OPERATION_TIMEOUT) {
            Log.e(TAG, logPrefix("pendingGattOperationTimeoutSignal()"))
        }
        val pendingGattOperationInfo = pendingGattOperationTimeoutCancel()
        if (pendingGattOperationInfo == null) {
            if (VERBOSE_LOG_PENDING_OPERATION_TIMEOUT_UNEXPECTED) {
                Log.e(TAG, logPrefix("pendingGattOperationTimeoutSignal: pendingGattOperationInfo == null; ignoring"))
            }
            return false
        }
        if (pendingGattOperationInfo.runAfterSuccess != null) {
            Log.v(TAG, logPrefix("pendingGattOperationTimeoutSignal: mHandlerMain.post(pendingGattOperationInfo.runAfterSuccess)"))
            handlerMain.post(pendingGattOperationInfo.runAfterSuccess)
        }
        return true
    }

    private fun pendingGattOperationTimeout(message: Message) {
        if (VERBOSE_LOG_PENDING_OPERATION_TIMEOUT) {
            Log.e(TAG, logPrefix("pendingGattOperationTimeout(message=$message)"))
        }
        val pendingGattOperationInfo = reconnectIfConnecting()
        if (pendingGattOperationInfo == null) {
            if (VERBOSE_LOG_PENDING_OPERATION_TIMEOUT_UNEXPECTED) {
                Log.e(TAG, logPrefix("pendingGattOperationTimeoutSignal: pendingGattOperationInfo == null; ignoring"))
            }
            return
        }
        val operation = pendingGattOperationInfo.operation
        val startTimeMillis = pendingGattOperationInfo.startTimeMillis
        val elapsedMillis = System.currentTimeMillis() - startTimeMillis
        if (operation == GattOperation.CharacteristicSetNotification) {
            isWaitingForCharacteristicSetNotification = false
        }
        Log.w(TAG, logPrefix("pendingGattOperationTimeout: operation=$operation, elapsedMillis=$elapsedMillis; *TIMED OUT*"))
        onDeviceOperationTimeout(operation, pendingGattOperationInfo.timeoutMillis, elapsedMillis)
        if (pendingGattOperationInfo.runAfterFail != null) {
            handlerMain.post(pendingGattOperationInfo.runAfterFail)
        }
    }
}
