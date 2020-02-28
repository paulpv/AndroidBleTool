package com.github.paulpv.androidbletool.gatt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.github.paulpv.androidbletool.BluetoothUtils;
import com.github.paulpv.androidbletool.BuildConfig;
import com.github.paulpv.androidbletool.utils.ListenerManager;
import com.github.paulpv.androidbletool.utils.MyHandler;
import com.github.paulpv.androidbletool.utils.Runtime;
import com.github.paulpv.androidbletool.utils.Utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * This is admittedly largely based on circa 2014 https://github.com/NordicPlayground/puck-central-android/blob/master/PuckCentral/app/src/main/java/no/nordicsemi/puckcentral/bluetooth/gatt/GattManager.java
 * That code is elegantly simple...
 * ...perhaps too simple.
 * One other problem is that it creates an AsyncTask for every operation to handle possible
 * timeout.
 * This seems very heavy.
 * The below implementation handles timeouts
 * <p>
 * One gripe I have about Nordic's code is that it routes every operation through an AsyncTask.
 * That seems *VERY* heavy.
 */
public class GattHandler {
    private static final String TAG = Utils.Companion.TAG(GattHandler.class);

    @SuppressWarnings("WeakerAccess")
    public static boolean VERBOSE_LOG_CHARACTERISTIC_CHANGE = false;

    @SuppressWarnings("WeakerAccess")
    public static boolean VERBOSE_LOG_PENDING_OPERATION_TIMEOUT = false;
    @SuppressWarnings("WeakerAccess")
    public static boolean VERBOSE_LOG_PENDING_OPERATION_TIMEOUT_UNEXPECTED = false;

    //
    // NOTE:(pv) SweetBlue is approximately 13 seconds
    //
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_CONNECT_INTERNAL_TIMEOUT_MILLIS = 9 * 1000;

    @SuppressWarnings("WeakerAccess")
    public static int CONNECT_INTERNAL_TIMEOUT_MILLIS = DEFAULT_CONNECT_INTERNAL_TIMEOUT_MILLIS;

    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_CONNECT_EXTERNAL_TIMEOUT_MILLIS;

    static {
        if (BuildConfig.DEBUG) {
            DEFAULT_CONNECT_EXTERNAL_TIMEOUT_MILLIS = 60 * 1000;
        } else {
            DEFAULT_CONNECT_EXTERNAL_TIMEOUT_MILLIS = 17 * 1000;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_OPERATION_TIMEOUT_MILLIS = 5 * 1000;
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_DISCONNECT_TIMEOUT_MILLIS = 250;

    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_POST_DELAY_MILLIS = 0;

    @SuppressWarnings("WeakerAccess")
    public static int POST_DELAY_MILLIS = DEFAULT_POST_DELAY_MILLIS;

    /**
     * See https://github.com/NordicSemiconductor/Android-BLE-Library/blob/master/ble/src/main/java/no/nordicsemi/android/ble/BleManager.java#L649
     * <p>
     * The onConnectionStateChange event is triggered just after the Android connects to a device.
     * In case of bonded devices, the encryption is reestablished AFTER this callback is called.
     * Moreover, when the device has Service Changed indication enabled, and the list of services
     * has changed (e.g. using the DFU), the indication is received few hundred milliseconds later,
     * depending on the connection interval.
     * When received, Android will start performing a service discovery operation, internally,
     * and will NOT notify the app that services has changed.
     * <p>
     * If the gatt.discoverServices() method would be invoked here with no delay, if would return
     * cached services, as the SC indication wouldn't be received yet. Therefore, we have to
     * postpone the service discovery operation until we are (almost, as there is no such callback)
     * sure, that it has been handled. It should be greater than the time from
     * LLCP Feature Exchange to ATT Write for Service Change indication.
     * <p>
     * If your device does not use Service Change indication (for example does not have DFU)
     * the delay may be 0.
     * <p>
     * Please calculate the proper delay that will work in your solution.
     * <p>
     * For devices that are not bonded, but support paiing, a small delay is required on some
     * older Android versions (Nexus 4 with Android 5.1.1) when the device will send pairing
     * request just after connection. If so, we want to wait with the service discovery until
     * bonding is complete.
     * <p>
     * The default implementation returns 1600 ms for bonded and 300 ms when the device is not
     * bonded to be compatible with older versions of the library.
     */
    @SuppressWarnings("WeakerAccess")
    public static int getServiceDiscoveryDelay(final boolean bonded) {
        return bonded ? 1600 : 300;
    }

    private static int sDefaultConnectExternalTimeoutMillis = DEFAULT_CONNECT_EXTERNAL_TIMEOUT_MILLIS;
    private static int sDefaultOperationTimeoutMillis = DEFAULT_OPERATION_TIMEOUT_MILLIS;
    private static int sDefaultDisconnectTimeoutMillis = DEFAULT_DISCONNECT_TIMEOUT_MILLIS;

    @SuppressWarnings("unused")
    public static int getDefaultConnectTimeoutMillis() {
        return sDefaultConnectExternalTimeoutMillis;
    }

    @SuppressWarnings("unused")
    public static void setDefaultConnectTimeoutMillis(int timeoutMillis) {
        sDefaultConnectExternalTimeoutMillis = timeoutMillis;
    }

    @SuppressWarnings("unused")
    public static int getDefaultOperationTimeoutMillis() {
        return sDefaultOperationTimeoutMillis;
    }

    @SuppressWarnings("unused")
    public static void setDefaultOperationTimeoutMillis(int timeoutMillis) {
        sDefaultOperationTimeoutMillis = timeoutMillis;
    }

    @SuppressWarnings("unused")
    public static int getDefaultDisconnectTimeoutMillis() {
        return sDefaultDisconnectTimeoutMillis;
    }

    @SuppressWarnings("unused")
    public static void setDefaultDisconnectTimeoutMillis(int timeoutMillis) {
        sDefaultDisconnectTimeoutMillis = timeoutMillis;
    }

    /**
     * Well-Known UUID for <a href="https://developer.bluetooth.org/gatt/descriptors/Pages/DescriptorViewer.aspx?u=org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml">
     * Client Characteristic Configuration</a>
     */
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = GattUuids.CLIENT_CHARACTERISTIC_CONFIG.getUuid();

    /**
     * Various wrappers around {@link BluetoothGattCallback} methods
     */
    @SuppressWarnings("WeakerAccess")
    public static abstract class GattHandlerListener {
        public enum GattOperation {
            Connect,
            DiscoverServices,
            CharacteristicRead,
            CharacteristicWrite,
            CharacteristicSetNotification,
            ReadRemoteRssi,
        }

        /**
         * @param gattHandler   GattHandler
         * @param operation     GattOperation
         * @param timeoutMillis The requested timeout milliseconds
         * @param elapsedMillis The actual elapsed milliseconds
         * @return true to forcibly stay connected, false to allow disconnect
         */
        @SuppressWarnings("WeakerAccess")
        public boolean onDeviceOperationTimeout(@SuppressWarnings("unused") GattHandler gattHandler,
                                                @SuppressWarnings("unused") GattOperation operation,
                                                @SuppressWarnings("unused") long timeoutMillis,
                                                @SuppressWarnings("unused") long elapsedMillis) {
            return false;
        }

        /**
         * @param gattHandler GattHandler
         * @return true to forcibly disconnect, false to not forcibly disconnect
         */
        public boolean onDeviceConnecting(@SuppressWarnings("unused") GattHandler gattHandler) {
            return false;
        }

        /**
         * @param gattHandler   GattHandler
         * @param elapsedMillis long
         * @return true to forcibly disconnect, false to not forcibly disconnect
         */
        public boolean onDeviceConnected(@SuppressWarnings("unused") GattHandler gattHandler,
                                         @SuppressWarnings("unused") long elapsedMillis) {
            return false;
        }

        public enum DisconnectReason {
            ConnectFailed,
            SolicitedDisconnect,
            SolicitedDisconnectTimeout,
            UnsolicitedDisconnect,
        }

        /**
         * @param gattHandler   GattHandler
         * @param status        same as status in {@link BluetoothGattCallback#onConnectionStateChange(BluetoothGatt,
         *                      int, int)}, or -1 if unknown
         * @param reason        DisconnectReason
         * @param elapsedMillis elapsedMillis
         * @return true to automatically call {@link #removeListener(GattHandlerListener)}
         */
        public boolean onDeviceDisconnected(@SuppressWarnings("unused") GattHandler gattHandler,
                                            @SuppressWarnings("unused") int status,
                                            @SuppressWarnings("unused") DisconnectReason reason,
                                            @SuppressWarnings("unused") long elapsedMillis) {
            return false;
        }

        /**
         * @param gattHandler   GattHandler
         * @param services      List<BluetoothGattService>
         * @param success       if false, will always disconnect
         * @param elapsedMillis elapsedMillis
         * @return true to forcibly disconnect, false to not forcibly disconnect
         */
        public boolean onDeviceServicesDiscovered(@SuppressWarnings("unused") GattHandler gattHandler,
                                                  @SuppressWarnings("unused") List<BluetoothGattService> services,
                                                  @SuppressWarnings("unused") boolean success,
                                                  @SuppressWarnings("unused") long elapsedMillis) {
            return false;
        }

        /**
         * @param gattHandler    GattHandler
         * @param characteristic BluetoothGattCharacteristic
         * @param success        if false, will always disconnect
         * @param elapsedMillis  elapsedMillis
         * @return true to forcibly disconnect, false to not forcibly disconnect
         */
        public boolean onDeviceCharacteristicRead(@SuppressWarnings("unused") GattHandler gattHandler,
                                                  @SuppressWarnings("unused") BluetoothGattCharacteristic characteristic,
                                                  @SuppressWarnings("unused") boolean success,
                                                  @SuppressWarnings("unused") long elapsedMillis) {
            return false;
        }

        /**
         * @param gattHandler    GattHandler
         * @param characteristic BluetoothGattCharacteristic
         * @param success        if false, will always disconnect
         * @param elapsedMillis  elapsedMillis
         * @return true to forcibly disconnect, false to not forcibly disconnect
         */
        public boolean onDeviceCharacteristicWrite(@SuppressWarnings("unused") GattHandler gattHandler,
                                                   @SuppressWarnings("unused") BluetoothGattCharacteristic characteristic,
                                                   @SuppressWarnings("unused") boolean success,
                                                   @SuppressWarnings("unused") long elapsedMillis) {
            return false;
        }

        /**
         * @param gattHandler    GattHandler
         * @param characteristic BluetoothGattCharacteristic
         * @param success        if false, will always disconnect
         * @param elapsedMillis  elapsedMillis
         * @return true to forcibly disconnect, false to not forcibly disconnect
         */
        public boolean onDeviceCharacteristicSetNotification(@SuppressWarnings("unused") GattHandler gattHandler,
                                                             @SuppressWarnings("unused") BluetoothGattCharacteristic characteristic,
                                                             @SuppressWarnings("unused") boolean success,
                                                             @SuppressWarnings("unused") long elapsedMillis) {
            return false;
        }

        /**
         * @param gattHandler    GattHandler
         * @param characteristic BluetoothGattCharacteristic
         * @return true to forcibly disconnect, false to not forcibly disconnect
         */
        public boolean onDeviceCharacteristicChanged(@SuppressWarnings("unused") GattHandler gattHandler,
                                                     @SuppressWarnings("unused") BluetoothGattCharacteristic characteristic) {
            return false;
        }

        /**
         * @param gattHandler   GattHandler
         * @param rssi          rssi
         * @param success       if false, will always disconnect
         * @param elapsedMillis elapsedMillis
         * @return true to forcibly disconnect, false to not forcibly disconnect
         */
        public boolean onDeviceReadRemoteRssi(@SuppressWarnings("unused") GattHandler gattHandler,
                                              @SuppressWarnings("unused") int rssi,
                                              @SuppressWarnings("unused") boolean success,
                                              @SuppressWarnings("unused") long elapsedMillis) {
            return false;
        }
    }

    private final GattManager mGattManager;
    private final Context mContext;
    private final long mDeviceAddressLong;
    private final String mDeviceAddressString;
    /**
     * NOTE: All calls to GattHandlerListener methods should be **INSIDE** mHandlerMain's Looper thread
     */
    private final ListenerManager<GattHandlerListener> mListenerManager;
    private final MyHandler mHandlerMain;
    private final BluetoothAdapter mBluetoothAdapter;
    private final Map<GattHandlerListener.GattOperation, Long> mStartTimes;
    private final BluetoothGattCallback mBackgroundBluetoothGattCallback;

    /**
     * synchronized behind mGattManager
     */
    private BluetoothGatt mBluetoothGatt;
    /**
     * synchronized behind mGattManager
     */
    private boolean mIsSolicitedDisconnecting;

    //package
    GattHandler(GattManager gattManager, long deviceAddress) {
        Runtime.throwIllegalArgumentExceptionIfNull(gattManager, "gattManager");
        BluetoothUtils.Companion.throwExceptionIfInvalidBluetoothAddress(deviceAddress);

        mGattManager = gattManager;

        mContext = gattManager.getContext();

        mDeviceAddressLong = deviceAddress;
        mDeviceAddressString = BluetoothUtils.Companion.macAddressLongToString(deviceAddress);

        mListenerManager = new ListenerManager<>(this);

        mHandlerMain = new MyHandler(mGattManager.getLooper(), GattHandler.this::handleMessage);

        mBluetoothAdapter = BluetoothUtils.Companion.getBluetoothAdapter(mContext);

        mStartTimes = new HashMap<>();

        mBackgroundBluetoothGattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                GattHandler.this.onConnectionStateChange(gatt, status, newState);
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                GattHandler.this.onServicesDiscovered(gatt, status);
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                GattHandler.this.onCharacteristicRead(gatt, characteristic, status);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                GattHandler.this.onCharacteristicWrite(gatt, characteristic, status);
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                GattHandler.this.onDescriptorWrite(gatt, descriptor, status);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                GattHandler.this.onCharacteristicChanged(gatt, characteristic);
            }

            @Override
            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                GattHandler.this.onReadRemoteRssi(gatt, rssi, status);
            }
        };
    }

    //
    //
    //

    @SuppressWarnings("unused")
    public BluetoothAdapter getBluetoothAdapter() {
        return mBluetoothAdapter;
    }

    @SuppressWarnings({"WeakerAccess", "BooleanMethodIsAlwaysInverted"})
    public boolean isBluetoothAdapterEnabled(String callerName) {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, logPrefix(callerName + ": mBluetoothAdapter == null; ignoring"));
            return false;
        }

        try {
            if (!mBluetoothAdapter.isEnabled()) {
                Log.w(TAG, logPrefix(callerName + ": mBluetoothAdapter.isEnabled() == false; ignoring"));
                return false;
            }

            return true;
        } catch (Exception e) {
            Log.w(TAG, logPrefix(callerName + ": EXCEPTION mBluetoothAdapter.isEnabled()"), e);
            return false;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public long getDeviceAddressLong() {
        return mDeviceAddressLong;
    }

    @SuppressWarnings("unused")
    public String getDeviceAddressString() {
        return mDeviceAddressString;
    }

    @SuppressWarnings("unused")
    public BluetoothDevice getBluetoothDevice() {
        BluetoothGatt gatt = getBluetoothGatt(true);
        return gatt != null ? gatt.getDevice() : null;
    }

    @SuppressWarnings("WeakerAccess")
    public void close() {
        close(true);
    }

    //package
    void close(boolean remove) {
        synchronized (mGattManager) {
            if (remove) {
                mGattManager.removeGattHandler(this);
            }
            disconnect(null);
        }
    }

    private String logPrefix(String message) {
        return mDeviceAddressString + " " + message;
    }

    private boolean isMainThread() {
        return mHandlerMain.getLooper() == Looper.myLooper();
    }

    public boolean isConnectingOrConnectedAndNotDisconnecting() {
        return isConnectingOrConnectedAndNotDisconnecting("{public}");
    }

    private boolean isConnectingOrConnectedAndNotDisconnecting(@SuppressWarnings("SameParameterValue") String callerName) {
        return internalIsConnectingOrConnectedAndNotDisconnecting(callerName, null);
    }

    private boolean ignoreIfIsConnectingOrConnectedAndNotDisconnecting(String callerName) {
        return internalIsConnectingOrConnectedAndNotDisconnecting(callerName, "ignoring");
    }

    private boolean internalIsConnectingOrConnectedAndNotDisconnecting(String callerName, String logSuffixIfTrue) {
        //Log.e(TAG, "isConnectingOrConnectedAndNotDisconnecting(callerName=" + callerName + ')');
        if (getBluetoothGatt(true) == null) {
            return false;
        }

        Log.w(TAG, logPrefix(callerName + ": isConnectingOrConnectedAndNotDisconnecting(...) == true" +
                (Utils.Companion.isNullOrEmpty(logSuffixIfTrue) ? "" : "; " + logSuffixIfTrue)));
        return true;
    }

    @SuppressWarnings("unused")
    public boolean isDisconnectingOrDisconnected(String callerName) {
        return internalIsDisconnectingOrDisconnected(callerName, null);
    }

    @SuppressWarnings("WeakerAccess")
    public boolean ignoreIfIsDisconnectingOrDisconnected(String callerName) {
        return internalIsDisconnectingOrDisconnected(callerName, "ignoring");
    }

    private boolean internalIsDisconnectingOrDisconnected(String callerName, String logSuffixIfTrue) {
        //Log.e(TAG, "isConnectingOrConnectedAndNotDisconnecting(callerName=" + callerName + ')');
        if (getBluetoothGatt(false) != null) {
            return false;
        }

        Log.w(TAG, logPrefix(callerName + ": isDisconnectingOrDisconnected(...) == true" +
                (Utils.Companion.isNullOrEmpty(logSuffixIfTrue) ? "" : "; " + logSuffixIfTrue)));
        return true;
    }

    @SuppressWarnings("unused")
    public boolean isDisconnecting() {
        return mIsSolicitedDisconnecting;
    }

    @SuppressWarnings("unused")
    public boolean isDisconnected() {
        return getBluetoothGatt(false) == null;
    }

    private void postDelayed(@NonNull Runnable runnable) {
        postDelayed(runnable, POST_DELAY_MILLIS);
    }

    private void postDelayed(@NonNull Runnable runnable, long delayMillis) {
        mHandlerMain.postDelayed(runnable, delayMillis);
    }

    private BluetoothGatt getBluetoothGatt(boolean onlyIfConnectingOrConnectedAndNotDisconnecting) {
        //Log.e(TAG, "getBluetoothGatt(onlyIfConnectingOrConnectedAndNotDisconnecting=" +
        //             onlyIfConnectingOrConnectedAndNotDisconnecting + ')');
        synchronized (mGattManager) {
            //Log.e(TAG, "getBluetoothGatt: mBluetoothGatt=" + mBluetoothGatt);
            BluetoothGatt gatt = mBluetoothGatt;
            if (gatt != null) {
                //Log.e(TAG, "getBluetoothGatt: mIsSolicitedDisconnecting=" + mIsSolicitedDisconnecting);
                if (onlyIfConnectingOrConnectedAndNotDisconnecting && mIsSolicitedDisconnecting) {
                    gatt = null;
                }
            }
            return gatt;
        }
    }

    //
    //
    //

    @SuppressWarnings("unused")
    public void addListener(GattHandlerListener listener) {
        Log.e(TAG, logPrefix("addListener " + listener));
        mListenerManager.attach(listener);
    }

    @SuppressWarnings("WeakerAccess")
    public void removeListener(GattHandlerListener listener) {
        Log.e(TAG, logPrefix("removeListener " + listener));
        mListenerManager.detach(listener);
    }

    @SuppressWarnings("unused")
    public void clearListeners() {
        mListenerManager.clear();
    }

    private long timerStart(GattHandlerListener.GattOperation operation) {
        //Log.e(TAG, logPrefix("timerStart(operation=" + operation + ')'));
        long startTimeMillis = System.currentTimeMillis();
        //Log.e(TAG, logPrefix("timerStart: startTimeMillis=" + startTimeMillis));
        mStartTimes.put(operation, startTimeMillis);
        return startTimeMillis;
    }

    private long timerElapsed(GattHandlerListener.GattOperation operation, boolean remove) {
        //Log.e(TAG, logPrefix("timerElapsed(operation=" + operation + ", remove=" + remove + ')'));
        long elapsedMillis = -1;
        Long startTimeMillis;
        if (remove) {
            startTimeMillis = mStartTimes.remove(operation);
        } else {
            startTimeMillis = mStartTimes.get(operation);
        }
        //Log.e(TAG, logPrefix("timerElapsed: startTimeMillis=" + startTimeMillis));
        if (startTimeMillis != null) {
            elapsedMillis = System.currentTimeMillis() - startTimeMillis;
        }
        //Log.e(TAG, logPrefix("timerElapsed: elapsedMillis=" + elapsedMillis));
        return elapsedMillis;
    }

    //
    //
    //

    /**
     * @return true if the connect request was enqueued, otherwise false
     */
    @SuppressWarnings("unused")
    public boolean connect() {
        return connect(sDefaultConnectExternalTimeoutMillis, null, null);
    }

    /**
     * @return true if the connect request was enqueued, otherwise false
     */
    public boolean connect(Runnable runAfterConnect, Runnable runAfterFail) {
        return connect(sDefaultConnectExternalTimeoutMillis, runAfterConnect, runAfterFail);
    }

    /**
     * @param timeoutMillis long
     * @return true if the connect request was enqueued, otherwise false
     */
    @SuppressWarnings("WeakerAccess")
    public boolean connect(long timeoutMillis, Runnable runAfterConnect, Runnable runAfterFail) {
        return connect(false, timeoutMillis, runAfterConnect, runAfterFail);
    }

    /**
     * @param autoConnect boolean
     * @return true if the connect request was enqueued, otherwise false
     */
    @SuppressWarnings("unused")
    public boolean connect(boolean autoConnect, Runnable runAfterConnect, Runnable runAfterFail) {
        return connect(autoConnect, sDefaultConnectExternalTimeoutMillis, runAfterConnect, runAfterFail);
    }

    private boolean mConnectAutoConnect;
    private long mConnectExternalTimeoutMillis;
    private Runnable mConnectRunAfterConnect;
    private Runnable mConnectRunAfterFail;
    private long mConnectStartTimeMillis;

    /**
     * @param autoConnect     boolean
     * @param timeoutMillis   long
     * @param runAfterConnect Runnable
     * @return true if already connecting/connected and *NOT* disconnecting, or the connect request was enqueued,
     * otherwise false
     */
    @SuppressWarnings("WeakerAccess")
    public boolean connect(final boolean autoConnect, final long timeoutMillis,
                           final Runnable runAfterConnect, final Runnable runAfterFail) {
        if (!isBluetoothAdapterEnabled("connect")) {
            return false;
        }

        if (ignoreIfIsConnectingOrConnectedAndNotDisconnecting("connect")) {
            return true;
        }

        mConnectAutoConnect = autoConnect;
        mConnectExternalTimeoutMillis = timeoutMillis;
        mConnectRunAfterConnect = runAfterConnect;
        mConnectRunAfterFail = runAfterFail;
        mConnectStartTimeMillis = timerStart(GattHandlerListener.GattOperation.Connect);

        return connectInternal("connect", true, mConnectStartTimeMillis, mConnectAutoConnect, mConnectRunAfterConnect, mConnectRunAfterFail);
    }

    private PendingGattOperationInfo reconnectIfConnecting() {
        PendingGattOperationInfo pendingGattOperationInfo = pendingGattOperationTimeoutCancel();
        if (pendingGattOperationInfo == null) {
            return null;
        }

        switch (pendingGattOperationInfo.operation) {
            case Connect:
            case DiscoverServices:
                // continue
                break;
            default:
                return null;
        }

        long startTimeMillis = pendingGattOperationInfo.startTimeMillis;
        long elapsedMillis = System.currentTimeMillis() - startTimeMillis;

        boolean reconnecting = elapsedMillis < mConnectExternalTimeoutMillis;

        if (reconnecting) {
            pendingGattOperationInfo = null;

            postDelayed(() -> {
                GattUtils.Companion.safeDisconnect("reconnectIfConnecting", mBluetoothGatt);
                postDelayed(() -> {
                    if (!connectInternal("reconnectIfConnecting", false, mConnectStartTimeMillis, mConnectAutoConnect, mConnectRunAfterConnect, mConnectRunAfterFail)) {
                        Log.e(TAG, logPrefix("reconnectIfConnecting: failed to request reconnect"));
                    }
                });
            });
        }

        return pendingGattOperationInfo;
    }

    private boolean connectInternal(String callerName,
                                    boolean ignoreIfIsConnectingOrConnectedAndNotDisconnecting,
                                    final long startTimeMillis,
                                    final boolean autoConnect,
                                    final Runnable runAfterConnect, final Runnable runAfterFail) {
        Log.i(TAG, logPrefix("connectInternal(callerName=" + Utils.Companion.quote(callerName) +
                ", autoConnect=" + autoConnect +
                ", runAfterConnect=" + runAfterConnect +
                ", runAfterFail=" + runAfterFail + ')'));

        final String callerNameFinal = "connectInternal." + callerName;

        if (!isBluetoothAdapterEnabled(callerNameFinal)) {
            return false;
        }

        if (ignoreIfIsConnectingOrConnectedAndNotDisconnecting) {
            if (ignoreIfIsConnectingOrConnectedAndNotDisconnecting(callerNameFinal)) {
                return true;
            }
        }

        //final GattOperation operation = GattOperation.Connect;
        final GattHandlerListener.GattOperation operation = GattHandlerListener.GattOperation.DiscoverServices;
        postDelayed(() -> {
            try {
                Log.v(TAG, logPrefix("+" + callerNameFinal + ".run(): autoConnect=" + autoConnect));

                if (ignoreIfIsConnectingOrConnectedAndNotDisconnecting) {
                    if (ignoreIfIsConnectingOrConnectedAndNotDisconnecting(callerNameFinal + ".run")) {
                        return;
                    }
                }

                pendingGattOperationTimeoutReset(callerNameFinal);

                BluetoothDevice bluetoothDevice = mBluetoothAdapter.getRemoteDevice(mDeviceAddressString);

                onDeviceConnecting();

                //
                // NOTE: Some Gatt operations, especially connecting, can take "up to" (meaning "over") 30 seconds, per:
                //  http://stackoverflow.com/a/18889509/252308
                //  http://e2e.ti.com/support/wireless_connectivity/f/538/p/281081/985950#985950
                //

                synchronized (mGattManager) {
                    //
                    // NOTE:(pv) mBluetoothGatt is only set here and in #onDeviceDisconnected
                    //
                    if (mBluetoothGatt != null) {
                        GattUtils.Companion.safeDisconnect(callerNameFinal + ".run: GattUtils.safeDisconnect(mBluetoothGatt)", mBluetoothGatt);

                        Log.v(TAG, logPrefix(callerNameFinal + ".run: +mBluetoothGatt.connect()"));
                        mBluetoothGatt.connect();
                        Log.v(TAG, logPrefix(callerNameFinal + ".run: -mBluetoothGatt.connect()"));
                    } else {
                        Log.v(TAG, logPrefix(callerNameFinal + ".run: +bluetoothDevice.connectGatt(...)"));
                        BluetoothGattCompat bluetoothGattCompat = new BluetoothGattCompat(mContext);
                        mBluetoothGatt = bluetoothGattCompat.connectGatt(bluetoothDevice, autoConnect, mBackgroundBluetoothGattCallback);
                        Log.v(TAG, logPrefix(callerNameFinal + ".run: -bluetoothDevice.connectGatt(...) returned " + mBluetoothGatt));
                    }
                }

                if (mBluetoothGatt == null) {
                    Log.w(TAG, logPrefix(callerNameFinal + ".run: bluetoothDevice.connectGatt(...) failed"));
                    onDeviceDisconnected(null, -1, GattHandlerListener.DisconnectReason.ConnectFailed, false, null);
                } else {
                    // Internally timeout every CONNECT_INTERNAL_TIMEOUT_MILLIS and repeat until mConnectExternalTimeoutMillis is exceeded
                    pendingGattOperationTimeoutSchedule(operation, startTimeMillis, CONNECT_INTERNAL_TIMEOUT_MILLIS, runAfterConnect, runAfterFail);
                }
            } finally {
                Log.v(TAG, logPrefix("-" + callerNameFinal + ".run(): autoConnect=" + autoConnect));
            }
        });

        return true;
    }

    public boolean disconnect(Runnable runAfterDisconnect) {
        return disconnect(sDefaultDisconnectTimeoutMillis, runAfterDisconnect);
    }

    @SuppressWarnings("WeakerAccess")
    public boolean disconnect(final long timeoutMillis, Runnable runAfterDisconnect) {
        try {
            Log.i(TAG, logPrefix("+disconnect(timeoutMillis=" + timeoutMillis + ", runAfterDisconnect=" + runAfterDisconnect + ')'));

            synchronized (mGattManager) {
                if (mBluetoothGatt == null) {
                    Log.w(TAG, logPrefix("disconnect: mBluetoothGatt == null; ignoring"));
                    return false;
                }

                if (mIsSolicitedDisconnecting) {
                    Log.w(TAG, logPrefix("disconnect: mIsSolicitedDisconnecting == true; ignoring"));
                    return false;
                }

                //
                // To be safe, always disconnect from the same thread that the connection was made on
                //
                if (!isMainThread()) {
                    Log.v(TAG, logPrefix("disconnect: isMainThread() == false; posting disconnect() to mHandlerMain;"));
                    postDelayed(() -> disconnect(timeoutMillis, runAfterDisconnect));
                    return false;
                }

                mIsSolicitedDisconnecting = true;

                //Log.e(TAG, logPrefix("+mBackgroundPendingOperationSignal.cancel()"));
                pendingGattOperationTimeoutCancel();
                //Log.e(TAG, logPrefix("-mBackgroundPendingOperationSignal.cancel()"));

                if (GattUtils.Companion.safeDisconnect("disconnect(timeoutMillis=" + timeoutMillis + ')', mBluetoothGatt)) {
                    //
                    // Timeout is needed since BluetoothGatt#disconnect() doesn't always call onConnectionStateChange(..., newState=STATE_DISCONNECTED)
                    //
                    mHandlerMain.obtainAndSendMessageDelayed(HandlerMainMessages.SolicitedDisconnectInternalTimeout, 0, 0, runAfterDisconnect, timeoutMillis);
                } else {
                    onDeviceDisconnected(mBluetoothGatt, -1, GattHandlerListener.DisconnectReason.SolicitedDisconnect, true, runAfterDisconnect);
                }
            }

            return true;
        } finally {
            Log.i(TAG, logPrefix("-disconnect(timeoutMillis=" + timeoutMillis + ", runAfterDisconnect=" + runAfterDisconnect + ')'));
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
    private void onDeviceDisconnected(BluetoothGatt gatt,
                                      final int status,
                                      final GattHandlerListener.DisconnectReason reason,
                                      boolean logStatusAndState,
                                      Runnable runAfterDisconnect) {
        Log.v(TAG, logPrefix("onDeviceDisconnected(gatt, status=" + status +
                ", reason=" + reason +
                ", logStatusAndState=" + logStatusAndState +
                ", runAfterDisconnect=" + runAfterDisconnect +
                ')'));

        synchronized (mGattManager) {
            if (mBluetoothGatt == null) {
                Log.w(TAG, logPrefix("onDeviceDisconnected: mBluetoothGatt == null; ignoring"));
                return;
            }

            // Only set here and in #connect
            mBluetoothGatt = null;

            final int elapsedMillis = (int) timerElapsed(GattHandlerListener.GattOperation.Connect, true);

            pendingGattOperationTimeoutCancel();

            mHandlerMain.removeMessages(HandlerMainMessages.SolicitedDisconnectInternalTimeout);

            mStartTimes.clear();

            if (logStatusAndState) {
                logStatusIfNotSuccess("onDeviceDisconnected", status, null);
            }

            mIsWaitingForCharacteristicSetNotification = false;

            // Only set here and in #disconnect
            mIsSolicitedDisconnecting = false;

            GattUtils.Companion.safeClose("onDeviceDisconnected", gatt);

            postDelayed(() -> {
                if (mBluetoothGatt != null) {
                    Log.w(TAG, logPrefix("onDeviceDisconnected: mBluetoothGatt != null; ignoring"));
                    return;
                }

                Log.v(TAG, logPrefix("onDeviceDisconnected: +deviceListener(s).onDeviceDisconnected(...)"));
                for (GattHandlerListener deviceListener : mListenerManager.beginTraversing()) {
                    if (deviceListener.onDeviceDisconnected(GattHandler.this,
                            status,
                            reason,
                            elapsedMillis)) {
                        removeListener(deviceListener);
                    }
                }
                mListenerManager.endTraversing();
                Log.v(TAG, logPrefix("onDeviceDisconnected: -deviceListener(s).onDeviceDisconnected(...)"));

                if (runAfterDisconnect != null) {
                    runAfterDisconnect.run();
                }
            });
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
    private void logStatusIfNotSuccess(String callerName, int status, String text) {
        String message;

        switch (status) {
            case BluetoothGatt.GATT_SUCCESS:
                // ignore
                return;
            case 133:
                //
                // https://code.google.com/p/android/issues/detail?id=58381
                // Too many device connections? (hard coded limit of ~4-7ish?)
                // NOTE:(pv) This problem can supposedly be sometimes induced by not calling "gatt.close()" when disconnecting
                //
                message = "Got the status 133 bug (too many connections?); see https://code.google.com/p/android/issues/detail?id=58381";
                break;
            case 257:
                //
                // https://code.google.com/p/android/issues/detail?id=183108
                // NOTE:(pv) This problem can supposedly be sometimes induced by calling "gatt.close()" before "onConnectionStateChange" is called by "gatt.disconnect()"
                //
                message = "Got the status 257 bug (disconnect()->close()); see https://code.google.com/p/android/issues/detail?id=183108";
                break;
            default:
                message = "error status=" + status;
                break;
        }

        if (!Utils.Companion.isNullOrEmpty(text)) {
            message += ' ' + text;
        }

        Log.e(TAG, logPrefix(callerName + ": " + message));
    }

    /**
     * NOTE: Some status codes can be found at...
     * https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/master/stack/include/gatt_api.h
     * ...not that they are very descriptive or helpful or anything like that! :/
     * <p/>
     * See {@link BluetoothGattCallback#onConnectionStateChange(BluetoothGatt, int, int)}
     */
    @SuppressWarnings("RedundantSuppression")
    private void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        String newStateString = BluetoothUtils.Companion.bluetoothProfileStateToString(newState);

        Log.v(TAG, logPrefix("onConnectionStateChange(gatt, status=" + status +
                ", newState=" + newStateString + ')'));

        //noinspection ConstantConditions,PointlessBooleanExpression
        if (true && false) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.e(TAG, logPrefix("onConnectionStateChange: ignoring newState == BluetoothProfile.STATE_CONNECTED to fake connection timeout"));
                return;
            }
        }

        //final int DEBUG_FAKE_STATUS_ERROR = 257;//....
        //final int DEBUG_FAKE_STATUS_ERROR = 133;
        final int DEBUG_FAKE_STATUS_ERROR = BluetoothGatt.GATT_SUCCESS;
        //noinspection ConstantConditions
        if (BuildConfig.DEBUG && DEBUG_FAKE_STATUS_ERROR != BluetoothGatt.GATT_SUCCESS) {
            status = DEBUG_FAKE_STATUS_ERROR;
            Log.e(TAG, logPrefix("onConnectionStateChange: ***FAKE*** STATUS " + status + " ERROR"));
        } else if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e(TAG, logPrefix("onConnectionStateChange: ***REAL*** STATUS " + status + " ERROR"));
        }

        final int finalStatus = status;

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

            onDeviceConnected();

            boolean bonded = gatt.getDevice().getBondState() == BluetoothDevice.BOND_BONDED;
            int serviceDiscoveryDelay = getServiceDiscoveryDelay(bonded);
            postDelayed(() -> {
                if (mBluetoothGatt == null) {
                    // Ensure that we will not try to discover services for a lost connection.
                    return;
                }
                timerStart(GattHandlerListener.GattOperation.DiscoverServices);
                if (!mBluetoothGatt.discoverServices()) {
                    Log.e(TAG, logPrefix("onConnectionStateChange: gatt.discoverServices() failed; disconnecting..."));
                    onDeviceDisconnected(mBluetoothGatt, finalStatus, GattHandlerListener.DisconnectReason.UnsolicitedDisconnect, false, null);
                }
            }, serviceDiscoveryDelay);

            return;
        }

        boolean reconnecting = reconnectIfConnecting() == null;

        if (newState != BluetoothProfile.STATE_DISCONNECTED) {
            Log.e(TAG, logPrefix("onConnectionStateChange: UNEXPECTED newState=" + newStateString + ", status=" + status +
                    "; " + (reconnecting ? "reconnecting" : "disconnecting") + "..."));
        }

        if (reconnecting) {
            return;
        }

        synchronized (mGattManager) {
            GattHandlerListener.DisconnectReason reason = mIsSolicitedDisconnecting ?
                    GattHandlerListener.DisconnectReason.SolicitedDisconnect : GattHandlerListener.DisconnectReason.UnsolicitedDisconnect;
            onDeviceDisconnected(gatt, status, reason, true, null);
        }
    }

    private void onDeviceOperationTimeout(final GattHandlerListener.GattOperation gattOperation,
                                          final long timeoutMillis,
                                          final long elapsedMillis) {
        postDelayed(() -> {
            boolean disconnect = true;

            for (GattHandlerListener deviceListener : mListenerManager.beginTraversing()) {
                if (deviceListener.onDeviceOperationTimeout(GattHandler.this,
                        gattOperation,
                        timeoutMillis,
                        elapsedMillis)) {
                    disconnect = false;
                }
            }
            mListenerManager.endTraversing();

            if (disconnect) {
                disconnect(null);
            }
        });
    }

    private void onDeviceConnecting() {
        postDelayed(() -> {
            boolean disconnect = false;

            for (GattHandlerListener deviceListener : mListenerManager.beginTraversing()) {
                disconnect |= deviceListener.onDeviceConnecting(GattHandler.this);
            }
            mListenerManager.endTraversing();

            Log.v(TAG, logPrefix("onDeviceConnecting: disconnect=" + disconnect));
            if (disconnect) {
                disconnect(null);
            }
        });
    }

    private void onDeviceConnected() {
        final long elapsedMillis = timerElapsed(GattHandlerListener.GattOperation.Connect, false);

        postDelayed(() -> {
            boolean disconnect = false;

            for (GattHandlerListener deviceListener : mListenerManager.beginTraversing()) {
                disconnect |= deviceListener.onDeviceConnected(GattHandler.this,
                        elapsedMillis);
            }
            mListenerManager.endTraversing();

            Log.v(TAG, logPrefix("onDeviceConnected: disconnect=" + disconnect));
            if (disconnect) {
                disconnect(null);
            }
        });
    }

    private void onServicesDiscovered(BluetoothGatt gatt, int status) {
        Log.v(TAG, logPrefix("onServicesDiscovered(gatt, status=" + status + ')'));

        int elapsedMillis = (int) timerElapsed(GattHandlerListener.GattOperation.DiscoverServices, true);

        logStatusIfNotSuccess("onServicesDiscovered", status, null);

        boolean success = status == BluetoothGatt.GATT_SUCCESS;

        List<BluetoothGattService> services;

        if (!success) {
            services = null;
        } else {
            services = gatt.getServices();
        }

        onDeviceServicesDiscovered(services, success, elapsedMillis);
    }

    @SuppressWarnings({"PointlessBooleanExpression", "ConstantConditions"})
    private static final boolean DEBUG_LOG_SERVICES_AND_CHARACTERISTICS = false && BuildConfig.DEBUG;

    private final Object lockLogServicesAndCharacteristics = new Object();

    private void logServicesAndCharacteristics(@SuppressWarnings("SameParameterValue") String callerName, BluetoothGatt gatt) {
        logServicesAndCharacteristics(callerName, gatt, null);
    }

    private void logServicesAndCharacteristics(String callerName, BluetoothGatt gatt, @SuppressWarnings("SameParameterValue") List<BluetoothGattService> services) {
        if (!DEBUG_LOG_SERVICES_AND_CHARACTERISTICS) {
            return;
        }
        if (gatt == null) {
            return;
        }
        synchronized (lockLogServicesAndCharacteristics) {
            if (services == null) {
                services = gatt.getServices();
            }

            //
            // Used to test BluetoothGatt.getService(uuidKnownToExistOnDevice) sometimes returning null
            //
            // https://stackoverflow.com/questions/41756294/android-bluetoothgatt-getservicesxyz-in-onservicesdiscovered-returns-null-for
            // https://stackoverflow.com/questions/56250434/bluetoothgatt-getserviceuuid-returns-null-when-i-know-the-device-offers-said
            //
            BluetoothGattService service;
            UUID uuid;
            BluetoothGattService serviceGet;
            List<BluetoothGattCharacteristic> characteristics;
            BluetoothGattCharacteristic characteristic;
            BluetoothGattCharacteristic characteristicGet;
            for (int i = 0, servicesSize = services.size(); i < servicesSize; i++) {
                service = services.get(i);
                // @formatter:off
                Log.v(TAG, logPrefix(callerName + ":                    services[" + Utils.Companion.formatNumber(i, 2) + "]=" + GattUuids.toString(service)));
                uuid = service.getUuid();
                Log.v(TAG, logPrefix(callerName + ":          services[" + Utils.Companion.formatNumber(i, 2) + "].getUuid()=" + uuid));
                serviceGet = gatt.getService(uuid);
                Log.v(TAG, logPrefix(callerName + ":           gatt.getService(uuid)=" + GattUuids.toString(serviceGet)));
                characteristics = service.getCharacteristics();
                for (int j = 0, characteristicsSize = characteristics.size(); j < characteristicsSize; j++) {
                    characteristic = characteristics.get(j);
                    Log.v(TAG, logPrefix(callerName + ":             characteristics[" + Utils.Companion.formatNumber(j, 2) + "]=" + GattUuids.toString((characteristic))));
                    uuid = characteristic.getUuid();
                    Log.v(TAG, logPrefix(callerName + ":   characteristics[" + Utils.Companion.formatNumber(j, 2) + "].getUuid()=" + uuid));
                    characteristicGet = service.getCharacteristic(uuid);
                    Log.v(TAG, logPrefix(callerName + ": service.getCharacteristic(uuid)=" + GattUuids.toString((characteristicGet))));
                }
                Log.v(TAG, logPrefix(callerName + ": ------------------------------------------"));
                // @formatter:on
            }
        }
    }

    private void onDeviceServicesDiscovered(final List<BluetoothGattService> services,
                                            final boolean success,
                                            final long elapsedMillis) {
        if (!pendingGattOperationTimeoutSignal()) {
            Log.w(TAG, logPrefix("onDeviceServicesDiscovered: pendingGattOperationTimeoutSignal() == false; ignoring"));
            return;
        }

        logServicesAndCharacteristics("onDeviceServicesDiscovered", mBluetoothGatt, services);

        postDelayed(() -> {
            boolean disconnect = false;

            for (GattHandlerListener deviceListener : mListenerManager.beginTraversing()) {
                disconnect |= deviceListener.onDeviceServicesDiscovered(GattHandler.this,
                        services,
                        success,
                        elapsedMillis);
            }
            mListenerManager.endTraversing();

            Log.v(TAG, logPrefix("onDeviceServicesDiscovered: success=" + success +
                    ", disconnect=" + disconnect));
            if (!success || disconnect) {
                disconnect(null);
            }
        });
    }

    //
    //
    //

    @SuppressWarnings("unused")
    public boolean characteristicRead(UUID serviceUuid, UUID characteristicUuid) {
        return characteristicRead(serviceUuid, characteristicUuid, sDefaultOperationTimeoutMillis, null, null);
    }

    @SuppressWarnings("unused")
    public boolean characteristicRead(UUID serviceUuid, UUID characteristicUuid, Runnable runAfterSuccess, Runnable runAfterFail) {
        return characteristicRead(serviceUuid, characteristicUuid, sDefaultOperationTimeoutMillis, runAfterSuccess, runAfterFail);
    }

    @SuppressWarnings("WeakerAccess")
    public boolean characteristicRead(final UUID serviceUuid, final UUID characteristicUuid,
                                      final long timeoutMillis,
                                      final Runnable runAfterSuccess, final Runnable runAfterFail) {
        Log.i(TAG, logPrefix("characteristicRead(serviceUuid=" + serviceUuid +
                ", characteristicUuid=" + characteristicUuid +
                ", timeoutMillis=" + timeoutMillis +
                ", runAfterSuccess=" + runAfterSuccess +
                ", runAfterFail=" + runAfterFail + ')'));

        Runtime.throwIllegalArgumentExceptionIfNull(serviceUuid, "serviceUuid");

        Runtime.throwIllegalArgumentExceptionIfNull(characteristicUuid, "characteristicUuid");

        if (!isBluetoothAdapterEnabled("characteristicRead")) {
            return false;
        }

        if (ignoreIfIsDisconnectingOrDisconnected("characteristicRead")) {
            return false;
        }

        final GattHandlerListener.GattOperation operation = GattHandlerListener.GattOperation.CharacteristicRead;

        final long startTimeMillis = timerStart(operation);

        postDelayed(() -> {
            try {
                Log.v(TAG, logPrefix("+characteristicRead.run(): serviceUuid=" + serviceUuid +
                        ", characteristicUuid=" + characteristicUuid +
                        ", timeoutMillis=" + timeoutMillis));

                BluetoothGatt gatt = pendingGattOperationTimeoutReset("characteristicRead");
                if (gatt == null) {
                    onDeviceCharacteristicRead(serviceUuid, characteristicUuid, false);
                    return;
                }

                BluetoothGattService service = gatt.getService(serviceUuid);
                if (service == null) {
                    Log.e(TAG, logPrefix(
                            "characteristicRead.run: gatt.getService(" +
                                    serviceUuid + ") failed"));
                    onDeviceCharacteristicRead(serviceUuid, characteristicUuid, false);
                    return;
                }

                BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
                if (characteristic == null) {
                    Log.e(TAG, logPrefix(
                            "characteristicRead.run: service.getCharacteristic(" +
                                    characteristicUuid + ") failed"));
                    onDeviceCharacteristicRead(serviceUuid, characteristicUuid, false);
                    return;
                }

                if (!gatt.readCharacteristic(characteristic)) {
                    Log.e(TAG, logPrefix(
                            "characteristicRead.run: gatt.characteristicRead(...) failed for characteristic " +
                                    characteristicUuid));
                    onDeviceCharacteristicRead(serviceUuid, characteristicUuid, false);
                    return;
                }

                pendingGattOperationTimeoutSchedule(operation, startTimeMillis, timeoutMillis, runAfterSuccess, runAfterFail);
            } finally {
                Log.v(TAG, logPrefix("-characteristicRead.run(): serviceUuid=" + serviceUuid +
                        ", characteristicUuid=" + characteristicUuid +
                        ", timeoutMillis=" + timeoutMillis));
            }
        });

        return true;
    }

    private void onCharacteristicRead(@SuppressWarnings("unused") BluetoothGatt gatt,
                                      BluetoothGattCharacteristic characteristic,
                                      int status) {
        UUID characteristicUuid = characteristic.getUuid();
        Log.v(TAG, logPrefix("onCharacteristicRead(gatt, characteristic=" + characteristicUuid +
                ", status=" + status + ')'));

        logStatusIfNotSuccess("onCharacteristicRead", status, "for characteristic " + characteristicUuid);

        boolean success = status == BluetoothGatt.GATT_SUCCESS;

        onDeviceCharacteristicRead(characteristic, success);
    }

    private void onDeviceCharacteristicRead(UUID serviceUuid, UUID characteristicUuid,
                                            @SuppressWarnings("SameParameterValue") boolean success) {
        BluetoothGattCharacteristic characteristic = GattUtils.Companion.createBluetoothGattCharacteristic(serviceUuid, characteristicUuid);
        onDeviceCharacteristicRead(characteristic, success);
    }

    private void onDeviceCharacteristicRead(final BluetoothGattCharacteristic characteristic,
                                            final boolean success) {
        if (!pendingGattOperationTimeoutSignal()) {
            Log.w(TAG, logPrefix("onDeviceCharacteristicRead: pendingGattOperationTimeoutSignal() == false; ignoring"));
            return;
        }

        final long elapsedMillis = timerElapsed(GattHandlerListener.GattOperation.CharacteristicRead, true);

        postDelayed(() -> {
            boolean disconnect = false;

            for (GattHandlerListener deviceListener : mListenerManager.beginTraversing()) {
                disconnect |= deviceListener.onDeviceCharacteristicRead(GattHandler.this,
                        characteristic,
                        success,
                        elapsedMillis);
            }
            mListenerManager.endTraversing();

            Log.v(TAG, logPrefix("onDeviceCharacteristicRead: success=" + success +
                    ", disconnect=" + disconnect));
            if (!success || disconnect) {
                disconnect(null);
            }
        });
    }

    //
    //
    //

    public enum CharacteristicWriteType {
        /**
         * Results in writing a {@link BluetoothGattCharacteristic#WRITE_TYPE_DEFAULT}
         */
        DefaultWithResponse,
        /**
         * Results in writing a {@link BluetoothGattCharacteristic#WRITE_TYPE_NO_RESPONSE}
         */
        WithoutResponse,
        /**
         * Results in writing a {@link BluetoothGattCharacteristic#WRITE_TYPE_SIGNED}
         */
        Signed,
    }

    /**
     * @param serviceUuid        UUID
     * @param characteristicUuid UUID
     * @param value              String
     */
    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       String value) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, null, null);
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       String value,
                                       Runnable runAfterSuccess, Runnable runAfterFail) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, null, runAfterSuccess, runAfterFail);
    }

    /**
     * @param serviceUuid             UUID
     * @param characteristicUuid      UUID
     * @param value                   String
     * @param characteristicWriteType null to ignore
     */
    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       String value,
                                       CharacteristicWriteType characteristicWriteType) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, characteristicWriteType, sDefaultOperationTimeoutMillis, null, null);
    }

    @SuppressWarnings("WeakerAccess")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       String value,
                                       CharacteristicWriteType characteristicWriteType,
                                       Runnable runAfterSuccess, Runnable runAfterFail) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, characteristicWriteType, sDefaultOperationTimeoutMillis, runAfterSuccess, runAfterFail);
    }

    /**
     * @param serviceUuid        UUID
     * @param characteristicUuid UUID
     * @param value              String
     * @param timeoutMillis      long
     */
    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       String value,
                                       long timeoutMillis) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, null, timeoutMillis, null, null);
    }

    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       String value,
                                       long timeoutMillis,
                                       Runnable runAfterSuccess, Runnable runAfterFail) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, null, timeoutMillis, runAfterSuccess, runAfterFail);
    }

    /**
     * @param serviceUuid             UUID
     * @param characteristicUuid      UUID
     * @param value                   String
     * @param characteristicWriteType null to ignore
     * @param timeoutMillis           long
     */
    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       String value,
                                       CharacteristicWriteType characteristicWriteType,
                                       long timeoutMillis) {
        return characteristicWrite(serviceUuid, characteristicUuid, GattUtils.Companion.toBytes(value), characteristicWriteType, timeoutMillis, null, null);
    }

    @SuppressWarnings("WeakerAccess")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       String value,
                                       CharacteristicWriteType characteristicWriteType,
                                       long timeoutMillis,
                                       Runnable runAfterSuccess, Runnable runAfterFail) {
        return characteristicWrite(serviceUuid, characteristicUuid, GattUtils.Companion.toBytes(value), characteristicWriteType, timeoutMillis, runAfterSuccess, runAfterFail);
    }

    /**
     * @param serviceUuid        UUID
     * @param characteristicUuid UUID
     * @param value              int
     * @param formatType         One of BluetoothGattCharacteristic.FORMAT_*
     * @param offset             int
     */
    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       int value, int formatType, int offset) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, formatType, offset, null, null, null);
    }

    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       int value, int formatType, int offset,
                                       Runnable runAfterSuccess, Runnable runAfterFail) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, formatType, offset, null, runAfterSuccess, runAfterFail);
    }

    /**
     * @param serviceUuid             UUID
     * @param characteristicUuid      UUID
     * @param value                   int
     * @param formatType              One of BluetoothGattCharacteristic.FORMAT_*
     * @param offset                  int
     * @param characteristicWriteType null to ignore
     */
    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       int value, int formatType, int offset,
                                       CharacteristicWriteType characteristicWriteType) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, formatType, offset, characteristicWriteType, sDefaultOperationTimeoutMillis, null, null);
    }

    @SuppressWarnings("WeakerAccess")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       int value, int formatType, int offset,
                                       CharacteristicWriteType characteristicWriteType,
                                       Runnable runAfterSuccess, Runnable runAfterFail) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, formatType, offset, characteristicWriteType, sDefaultOperationTimeoutMillis, runAfterSuccess, runAfterFail);
    }

    /**
     * @param serviceUuid        UUID
     * @param characteristicUuid UUID
     * @param value              int
     * @param formatType         One of BluetoothGattCharacteristic.FORMAT_*
     * @param offset             int
     * @param timeoutMillis      long
     */
    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       int value, int formatType, int offset,
                                       long timeoutMillis) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, formatType, offset, null, timeoutMillis, null, null);
    }

    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       int value, int formatType, int offset,
                                       long timeoutMillis,
                                       Runnable runAfterSuccess, Runnable runAfterFail) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, formatType, offset, null, timeoutMillis, runAfterSuccess, runAfterFail);
    }

    /**
     * @param serviceUuid             UUID
     * @param characteristicUuid      UUID
     * @param value                   int
     * @param formatType              One of BluetoothGattCharacteristic.FORMAT_*
     * @param offset                  int
     * @param characteristicWriteType null to ignore
     * @param timeoutMillis           long
     */
    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       int value, int formatType, int offset,
                                       CharacteristicWriteType characteristicWriteType,
                                       long timeoutMillis) {
        return characteristicWrite(serviceUuid, characteristicUuid, GattUtils.Companion.toBytes(value, formatType, offset), characteristicWriteType, timeoutMillis, null, null);
    }

    @SuppressWarnings("WeakerAccess")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       int value, int formatType, int offset,
                                       CharacteristicWriteType characteristicWriteType,
                                       long timeoutMillis,
                                       Runnable runAfterSuccess, Runnable runAfterFail) {
        return characteristicWrite(serviceUuid, characteristicUuid, GattUtils.Companion.toBytes(value, formatType, offset), characteristicWriteType, timeoutMillis, runAfterSuccess, runAfterFail);
    }

    /**
     * @param serviceUuid        UUID
     * @param characteristicUuid UUID
     * @param mantissa           int
     * @param exponent           int
     * @param formatType         One of BluetoothGattCharacteristic.FORMAT_*
     * @param offset             int
     */
    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       int mantissa, int exponent, int formatType, int offset) {
        return characteristicWrite(serviceUuid, characteristicUuid, mantissa, exponent, formatType, offset, null, null, null);
    }

    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       int mantissa, int exponent, int formatType, int offset,
                                       Runnable runAfterSuccess, Runnable runAfterFail) {
        return characteristicWrite(serviceUuid, characteristicUuid, mantissa, exponent, formatType, offset, null, runAfterSuccess, runAfterFail);
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
    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       int mantissa, int exponent, int formatType, int offset,
                                       CharacteristicWriteType characteristicWriteType) {
        return characteristicWrite(serviceUuid, characteristicUuid, mantissa, exponent, formatType, offset, characteristicWriteType, sDefaultOperationTimeoutMillis, null, null);
    }

    @SuppressWarnings("WeakerAccess")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       int mantissa, int exponent, int formatType, int offset,
                                       CharacteristicWriteType characteristicWriteType,
                                       Runnable runAfterSuccess, Runnable runAfterFail) {
        return characteristicWrite(serviceUuid, characteristicUuid, mantissa, exponent, formatType, offset, characteristicWriteType, sDefaultOperationTimeoutMillis, runAfterSuccess, runAfterFail);
    }

    /**
     * @param serviceUuid        UUID
     * @param characteristicUuid UUID
     * @param mantissa           int
     * @param exponent           int
     * @param formatType         One of BluetoothGattCharacteristic.FORMAT_*
     * @param offset             int
     * @param timeoutMillis      long
     */
    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       int mantissa, int exponent, int formatType, int offset,
                                       long timeoutMillis) {
        return characteristicWrite(serviceUuid, characteristicUuid, mantissa, exponent, formatType, offset, null, timeoutMillis, null, null);
    }

    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       int mantissa, int exponent, int formatType, int offset,
                                       long timeoutMillis,
                                       Runnable runAfterSuccess, Runnable runAfterFail) {
        return characteristicWrite(serviceUuid, characteristicUuid, mantissa, exponent, formatType, offset, null, timeoutMillis, runAfterSuccess, runAfterFail);
    }

    /**
     * @param serviceUuid             UUID
     * @param characteristicUuid      UUID
     * @param mantissa                int
     * @param exponent                int
     * @param formatType              One of BluetoothGattCharacteristic.FORMAT_*
     * @param offset                  int
     * @param characteristicWriteType null to ignore
     * @param timeoutMillis           long
     */
    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       int mantissa, int exponent, int formatType, int offset,
                                       CharacteristicWriteType characteristicWriteType,
                                       long timeoutMillis) {
        return characteristicWrite(serviceUuid, characteristicUuid, GattUtils.Companion.toBytes(mantissa, exponent, formatType, offset), characteristicWriteType, timeoutMillis, null, null);
    }

    @SuppressWarnings("WeakerAccess")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       int mantissa, int exponent, int formatType, int offset,
                                       CharacteristicWriteType characteristicWriteType,
                                       long timeoutMillis,
                                       Runnable runAfterSuccess, Runnable runAfterFail) {
        return characteristicWrite(serviceUuid, characteristicUuid, GattUtils.Companion.toBytes(mantissa, exponent, formatType, offset), characteristicWriteType, timeoutMillis, runAfterSuccess, runAfterFail);
    }

    /**
     * @param serviceUuid        UUID
     * @param characteristicUuid UUID
     * @param value              byte[]
     */
    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       byte[] value) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, null, null);
    }

    @SuppressWarnings("WeakerAccess")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       byte[] value,
                                       Runnable runAfterSuccess, Runnable runAfterFail) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, null, runAfterSuccess, runAfterFail);
    }

    /**
     * @param serviceUuid             UUID
     * @param characteristicUuid      UUID
     * @param value                   byte[]
     * @param characteristicWriteType null to ignore
     */
    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       byte[] value,
                                       CharacteristicWriteType characteristicWriteType) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, characteristicWriteType, sDefaultOperationTimeoutMillis, null, null);
    }

    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       byte[] value,
                                       CharacteristicWriteType characteristicWriteType,
                                       Runnable runAfterSuccess, Runnable runAfterFail) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, characteristicWriteType, sDefaultOperationTimeoutMillis, runAfterSuccess, runAfterFail);
    }

    /**
     * @param serviceUuid        UUID
     * @param characteristicUuid UUID
     * @param value              byte[]
     * @param timeoutMillis      long
     */
    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       byte[] value,
                                       long timeoutMillis) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, null, timeoutMillis, null, null);
    }

    @SuppressWarnings("unused")
    public boolean characteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                       byte[] value,
                                       long timeoutMillis,
                                       Runnable runAfterSuccess, Runnable runAfterFail) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, null, timeoutMillis, runAfterSuccess, runAfterFail);
    }

    /**
     * @param serviceUuid             UUID
     * @param characteristicUuid      UUID
     * @param value                   byte[]
     * @param characteristicWriteType null to ignore
     * @param timeoutMillis           long
     */
    @SuppressWarnings("unused")
    public boolean characteristicWrite(final UUID serviceUuid, final UUID characteristicUuid,
                                       final byte[] value,
                                       final CharacteristicWriteType characteristicWriteType,
                                       final long timeoutMillis) {
        return characteristicWrite(serviceUuid, characteristicUuid, value, characteristicWriteType, timeoutMillis, null, null);
    }

    @SuppressWarnings("WeakerAccess")
    public boolean characteristicWrite(final UUID serviceUuid, final UUID characteristicUuid,
                                       final byte[] value,
                                       final CharacteristicWriteType characteristicWriteType,
                                       final long timeoutMillis,
                                       final Runnable runAfterSuccess, final Runnable runAfterFail) {
        Log.i(TAG, logPrefix("characteristicWrite(serviceUuid=" + serviceUuid +
                ", characteristicUuid=" + characteristicUuid +
                ", value=" + Arrays.toString(value) +
                ", characteristicWriteType=" + characteristicWriteType +
                ", timeoutMillis=" + timeoutMillis +
                ", runAfterSuccess=" + runAfterSuccess +
                ", runAfterFail=" + runAfterFail + ')'));

        Runtime.throwIllegalArgumentExceptionIfNull(serviceUuid, "serviceUuid");

        Runtime.throwIllegalArgumentExceptionIfNull(characteristicUuid, "characteristicUuid");

        Runtime.throwIllegalArgumentExceptionIfNull(value, "value");

        if (!isBluetoothAdapterEnabled("characteristicWrite")) {
            return false;
        }

        if (ignoreIfIsDisconnectingOrDisconnected("characteristicWrite")) {
            return false;
        }

        final GattHandlerListener.GattOperation operation = GattHandlerListener.GattOperation.CharacteristicWrite;

        final long startTimeMillis = timerStart(operation);

        postDelayed(() -> {
            try {
                Log.v(TAG, logPrefix("+characteristicWrite.run(): serviceUuid=" + serviceUuid +
                        ", characteristicUuid=" + characteristicUuid +
                        ", value=" + Arrays.toString(value) +
                        ", characteristicWriteType=" + characteristicWriteType +
                        ", timeoutMillis=" + timeoutMillis));

                BluetoothGatt gatt = pendingGattOperationTimeoutReset("characteristicWrite");
                if (gatt == null) {
                    onDeviceCharacteristicWrite(serviceUuid, characteristicUuid, false);
                    return;
                }

                logServicesAndCharacteristics("characteristicWrite", gatt);

                BluetoothGattService service = gatt.getService(serviceUuid);
                if (service == null) {
                    Log.e(TAG, logPrefix(
                            "characteristicWrite.run: gatt.getService(" +
                                    serviceUuid + ") failed"));
                    onDeviceCharacteristicWrite(serviceUuid, characteristicUuid, false);
                    return;
                }

                BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
                if (characteristic == null) {
                    Log.e(TAG, logPrefix(
                            "characteristicWrite.run: service.getCharacteristic(" +
                                    characteristicUuid + ") failed"));
                    onDeviceCharacteristicWrite(serviceUuid, characteristicUuid, false);
                    return;
                }

                if (characteristicWriteType != null) {
                    int writeType;
                    switch (characteristicWriteType) {
                        case WithoutResponse:
                            writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
                            break;
                        case Signed:
                            writeType = BluetoothGattCharacteristic.WRITE_TYPE_SIGNED;
                            break;
                        case DefaultWithResponse:
                        default:
                            writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
                            break;
                    }
                    characteristic.setWriteType(writeType);
                }

                if (!characteristic.setValue(value)) {
                    Log.e(TAG, logPrefix("characteristicWrite: characteristic.setValue(" + Arrays.toString(value) +
                            " failed for characteristic " + characteristicUuid));
                    onDeviceCharacteristicWrite(serviceUuid, characteristicUuid, false);
                    return;
                }

                if (!gatt.writeCharacteristic(characteristic)) {
                    Log.e(TAG, logPrefix("characteristicWrite.run: gatt.characteristicWrite(...) failed for characteristic " + characteristicUuid));
                    onDeviceCharacteristicWrite(serviceUuid, characteristicUuid, false);
                    return;
                }

                pendingGattOperationTimeoutSchedule(operation, startTimeMillis, timeoutMillis, runAfterSuccess, runAfterFail);
            } finally {
                Log.v(TAG, logPrefix("-characteristicWrite.run(): serviceUuid=" + serviceUuid +
                        ", characteristicUuid=" + characteristicUuid +
                        ", value=" + Arrays.toString(value) +
                        ", characteristicWriteType=" + characteristicWriteType +
                        ", timeoutMillis=" + timeoutMillis));
            }
        });

        return true;
    }

    private void onCharacteristicWrite(@SuppressWarnings("unused") BluetoothGatt gatt,
                                       BluetoothGattCharacteristic characteristic, int status) {
        UUID characteristicUuid = characteristic.getUuid();
        Log.v(TAG, logPrefix("onCharacteristicWrite(gatt, characteristic=" + characteristicUuid +
                ", status=" + status + ')'));

        logStatusIfNotSuccess("onCharacteristicWrite", status, "for characteristic " + characteristicUuid);

        boolean success = status == BluetoothGatt.GATT_SUCCESS;

        onDeviceCharacteristicWrite(characteristic, success);
    }

    private void onDeviceCharacteristicWrite(UUID serviceUuid, UUID characteristicUuid,
                                             @SuppressWarnings("SameParameterValue") boolean success) {
        BluetoothGattCharacteristic characteristic = GattUtils.Companion.createBluetoothGattCharacteristic(serviceUuid, characteristicUuid);
        onDeviceCharacteristicWrite(characteristic, success);
    }

    private void onDeviceCharacteristicWrite(final BluetoothGattCharacteristic characteristic,
                                             final boolean success) {
        if (!pendingGattOperationTimeoutSignal()) {
            Log.w(TAG, logPrefix("onDeviceCharacteristicWrite: pendingGattOperationTimeoutSignal() == false; ignoring"));
            return;
        }

        final long elapsedMillis = timerElapsed(GattHandlerListener.GattOperation.CharacteristicWrite, true);

        postDelayed(() -> {
            boolean disconnect = false;

            for (GattHandlerListener deviceListener : mListenerManager.beginTraversing()) {
                disconnect |= deviceListener.onDeviceCharacteristicWrite(GattHandler.this,
                        characteristic,
                        success,
                        elapsedMillis);
            }
            mListenerManager.endTraversing();

            Log.v(TAG, logPrefix("onDeviceCharacteristicWrite: success=" + success +
                    ", disconnect=" + disconnect));
            if (!success || disconnect) {
                disconnect(null);
            }
        });
    }

    //
    //
    //

    public enum CharacteristicNotificationDescriptorType {
        /**
         * Results in writing a {@link BluetoothGattDescriptor#DISABLE_NOTIFICATION_VALUE}
         */
        Disable,
        /**
         * Results in writing a {@link BluetoothGattDescriptor#ENABLE_NOTIFICATION_VALUE}
         */
        EnableWithoutResponse,
        /**
         * Results in writing a {@link BluetoothGattDescriptor#ENABLE_INDICATION_VALUE}
         */
        EnableWithResponse,
    }

    @SuppressWarnings("unused")
    public boolean characteristicSetNotification(UUID serviceUuid, UUID characteristicUuid,
                                                 CharacteristicNotificationDescriptorType characteristicNotificationDescriptorType) {
        return characteristicSetNotification(serviceUuid, characteristicUuid, characteristicNotificationDescriptorType, null, null);
    }

    @SuppressWarnings("WeakerAccess")
    public boolean characteristicSetNotification(UUID serviceUuid, UUID characteristicUuid,
                                                 CharacteristicNotificationDescriptorType characteristicNotificationDescriptorType,
                                                 Runnable runAfterSuccess, Runnable runAfterFail) {
        return characteristicSetNotification(serviceUuid, characteristicUuid, characteristicNotificationDescriptorType, true, runAfterSuccess, runAfterFail);
    }

    @SuppressWarnings("unused")
    public boolean characteristicSetNotification(UUID serviceUuid, UUID characteristicUuid,
                                                 CharacteristicNotificationDescriptorType characteristicNotificationDescriptorType,
                                                 boolean setDescriptorClientCharacteristicConfig) {
        return characteristicSetNotification(serviceUuid, characteristicUuid, characteristicNotificationDescriptorType, setDescriptorClientCharacteristicConfig, null, null);
    }

    @SuppressWarnings("WeakerAccess")
    public boolean characteristicSetNotification(UUID serviceUuid, UUID characteristicUuid,
                                                 CharacteristicNotificationDescriptorType characteristicNotificationDescriptorType,
                                                 boolean setDescriptorClientCharacteristicConfig,
                                                 Runnable runAfterSuccess, Runnable runAfterFail) {
        return characteristicSetNotification(serviceUuid, characteristicUuid, characteristicNotificationDescriptorType, setDescriptorClientCharacteristicConfig, sDefaultOperationTimeoutMillis, runAfterSuccess, runAfterFail);
    }

    @SuppressWarnings("unused")
    public boolean characteristicSetNotification(final UUID serviceUuid, final UUID characteristicUuid,
                                                 final CharacteristicNotificationDescriptorType characteristicNotificationDescriptorType,
                                                 final boolean setDescriptorClientCharacteristicConfig,
                                                 final long timeoutMillis) {
        return characteristicSetNotification(serviceUuid, characteristicUuid, characteristicNotificationDescriptorType, setDescriptorClientCharacteristicConfig, timeoutMillis, null, null);
    }

    @SuppressWarnings("WeakerAccess")
    public boolean characteristicSetNotification(final UUID serviceUuid, final UUID characteristicUuid,
                                                 final CharacteristicNotificationDescriptorType characteristicNotificationDescriptorType,
                                                 final boolean setDescriptorClientCharacteristicConfig,
                                                 final long timeoutMillis,
                                                 final Runnable runAfterSuccess, final Runnable runAfterFail) {
        Log.i(TAG, logPrefix("characteristicSetNotification(serviceUuid=" + serviceUuid +
                ", characteristicUuid=" + characteristicUuid +
                ", characteristicNotificationDescriptorType=" + characteristicNotificationDescriptorType +
                ", setDescriptorClientCharacteristicConfig=" + setDescriptorClientCharacteristicConfig +
                ", timeoutMillis=" + timeoutMillis +
                ", runAfterSuccess=" + runAfterSuccess +
                ", runAfterFail=" + runAfterFail + ')'));

        Runtime.throwIllegalArgumentExceptionIfNull(serviceUuid, "serviceUuid");

        Runtime.throwIllegalArgumentExceptionIfNull(characteristicUuid, "characteristicUuid");

        Runtime.throwIllegalArgumentExceptionIfNull(characteristicNotificationDescriptorType, "characteristicNotificationDescriptorType");

        if (!isBluetoothAdapterEnabled("characteristicSetNotification")) {
            return false;
        }

        if (ignoreIfIsDisconnectingOrDisconnected("characteristicSetNotification")) {
            return false;
        }

        final GattHandlerListener.GattOperation operation = GattHandlerListener.GattOperation.CharacteristicSetNotification;

        final long startTimeMillis = timerStart(operation);

        postDelayed(() -> {
            try {
                Log.v(TAG, logPrefix(
                        "+characteristicSetNotification.run(): serviceUuid=" + serviceUuid +
                                ", characteristicUuid=" + characteristicUuid +
                                ", characteristicNotificationDescriptorType=" + characteristicNotificationDescriptorType +
                                ", setDescriptorClientCharacteristicConfig=" + setDescriptorClientCharacteristicConfig +
                                ", timeoutMillis=" + timeoutMillis));

                BluetoothGatt gatt = pendingGattOperationTimeoutReset("characteristicSetNotification");
                if (gatt == null) {
                    onDeviceCharacteristicSetNotification(serviceUuid, characteristicUuid, false);
                    return;
                }

                BluetoothGattService service = gatt.getService(serviceUuid);
                if (service == null) {
                    Log.e(TAG, logPrefix("characteristicSetNotification.run: gatt.getService(" + serviceUuid + ") failed"));
                    onDeviceCharacteristicSetNotification(serviceUuid, characteristicUuid, false);
                    return;
                }

                BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
                if (characteristic == null) {
                    Log.e(TAG, logPrefix(
                            "characteristicSetNotification.run: service.getCharacteristic(" + characteristicUuid + ") failed"));
                    onDeviceCharacteristicSetNotification(serviceUuid, characteristicUuid, false);
                    return;
                }

                boolean enable =
                        characteristicNotificationDescriptorType !=
                                CharacteristicNotificationDescriptorType.Disable;

                if (!gatt.setCharacteristicNotification(characteristic, enable)) {
                    Log.e(TAG, logPrefix(
                            "characteristicSetNotification.run: mGattConnectingOrConnected.characteristicSetNotification(..., enable=" + enable +
                                    ") failed for characteristic " + characteristicUuid));
                    onDeviceCharacteristicSetNotification(serviceUuid, characteristicUuid, false);
                    return;
                }

                if (!setDescriptorClientCharacteristicConfig) {
                    //
                    // Success
                    //
                    onDeviceCharacteristicSetNotification(serviceUuid, characteristicUuid, true);
                    return;
                }

                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                if (descriptor == null) {
                    Log.e(TAG, logPrefix(
                            "characteristicSetNotification.run: characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG" +
                                    ") failed for characteristic " + characteristicUuid));
                    onDeviceCharacteristicSetNotification(serviceUuid, characteristicUuid, false);
                    return;
                }

                byte[] descriptorValue;
                switch (characteristicNotificationDescriptorType) {
                    case EnableWithoutResponse:
                        descriptorValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                        break;
                    case EnableWithResponse:
                        descriptorValue = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                        break;
                    case Disable:
                    default:
                        descriptorValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                        break;
                }

                if (!descriptor.setValue(descriptorValue)) {
                    Log.e(TAG, logPrefix(
                            "characteristicSetNotification.run: descriptor.setValue(" + Arrays.toString(descriptorValue) +
                                    ") failed for descriptor CLIENT_CHARACTERISTIC_CONFIG for characteristic " + characteristicUuid));
                    onDeviceCharacteristicSetNotification(serviceUuid, characteristicUuid, false);
                    return;
                }

                if (!gatt.writeDescriptor(descriptor)) {
                    Log.e(TAG, logPrefix(
                            "characteristicSetNotification.run: mGattConnectingOrConnected.writeDescriptor(...) failed descriptor CLIENT_CHARACTERISTIC_CONFIG"));
                    onDeviceCharacteristicSetNotification(serviceUuid, characteristicUuid, false);
                    return;
                }

                mIsWaitingForCharacteristicSetNotification = true;

                pendingGattOperationTimeoutSchedule(operation, startTimeMillis, timeoutMillis, runAfterSuccess, runAfterFail);
            } finally {
                Log.v(TAG, logPrefix(
                        "-characteristicSetNotification.run(): serviceUuid=" + serviceUuid +
                                ", characteristicUuid=" + characteristicUuid +
                                ", characteristicNotificationDescriptorType=" + characteristicNotificationDescriptorType +
                                ", setDescriptorClientCharacteristicConfig=" + setDescriptorClientCharacteristicConfig +
                                ", timeoutMillis=" + timeoutMillis));
            }
        });

        return true;
    }

    private boolean mIsWaitingForCharacteristicSetNotification;

    private void onDescriptorWrite(@SuppressWarnings("unused") BluetoothGatt gatt,
                                   BluetoothGattDescriptor descriptor, int status) {
        if (!mIsWaitingForCharacteristicSetNotification) {
            //
            // ignore
            //
            return;
        }

        mIsWaitingForCharacteristicSetNotification = false;

        Log.v(TAG, logPrefix("onDescriptorWrite(gatt, descriptor=CLIENT_CHARACTERISTIC_CONFIG, status=" + status + ')'));

        BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();

        logStatusIfNotSuccess("onDescriptorWrite", status,
                "for descriptor CLIENT_CHARACTERISTIC_CONFIG for characteristic " + characteristic.getUuid());

        boolean success = status == BluetoothGatt.GATT_SUCCESS;

        onDeviceCharacteristicSetNotification(characteristic, success);
    }

    private void onDeviceCharacteristicSetNotification(UUID serviceUuid, UUID characteristicUuid,
                                                       boolean success) {
        BluetoothGattCharacteristic characteristic = GattUtils.Companion.createBluetoothGattCharacteristic(serviceUuid, characteristicUuid);
        onDeviceCharacteristicSetNotification(characteristic, success);
    }

    private void onDeviceCharacteristicSetNotification(final BluetoothGattCharacteristic characteristic,
                                                       final boolean success) {
        if (!pendingGattOperationTimeoutSignal()) {
            Log.w(TAG, logPrefix("onDeviceCharacteristicSetNotification: pendingGattOperationTimeoutSignal() == false; ignoring"));
            return;
        }

        final long elapsedMillis = timerElapsed(GattHandlerListener.GattOperation.CharacteristicSetNotification, true);

        postDelayed(() -> {
            boolean disconnect = false;

            for (GattHandlerListener deviceListener : mListenerManager.beginTraversing()) {
                disconnect |= deviceListener.onDeviceCharacteristicSetNotification(GattHandler.this,
                        characteristic,
                        success,
                        elapsedMillis);
            }
            mListenerManager.endTraversing();

            Log.v(TAG, logPrefix("onDeviceCharacteristicSetNotification: success=" + success + ", disconnect=" + disconnect));
            if (!success || disconnect) {
                disconnect(null);
            }
        });
    }

    private void onCharacteristicChanged(@SuppressWarnings("unused") BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic) {
        if (VERBOSE_LOG_CHARACTERISTIC_CHANGE) {
            Log.v(TAG, logPrefix("onCharacteristicChanged: characteristic=" + characteristic.getUuid()));
        }

        //
        // Handle the case where disconnect has been called, but the OS has queued up lots of characteristic changes
        //
        if (ignoreIfIsDisconnectingOrDisconnected("onCharacteristicChanged")) {
            return;
        }

        //
        // NOTE:(pv) This method may stream LOTS of data.
        // To avoid excessive memory allocations, this method intentionally deviates from the other methods' uses of
        // "mHandler.postDelayed(new Runnable() { ... }, DEFAULT_POST_DELAY_MILLIS)"
        //
        mHandlerMain.obtainAndSendMessage(HandlerMainMessages.onCharacteristicChanged, characteristic);
    }

    private static abstract class HandlerMainMessages {
        /**
         * <ul>
         * <li>msg.arg1: ?</li>
         * <li>msg.arg2: ?</li>
         * <li>msg.obj: ?</li>
         * </li>
         * </ul>
         */
        private static final int OperationTimeout = 1;

        /**
         * <ul>
         * <li>msg.arg1: ?</li>
         * <li>msg.arg2: ?</li>
         * <li>msg.obj: runAfterDisconnect</li>
         * </li>
         * </ul>
         */
        private static final int SolicitedDisconnectInternalTimeout = 2;

        /**
         * <ul>
         * <li>msg.arg1: ?</li>
         * <li>msg.arg2: ?</li>
         * <li>msg.obj: BluetoothGattCharacteristic</li>
         * </li>
         * </ul>
         */
        private static final int onCharacteristicChanged = 3;
    }

    private boolean handleMessage(Message msg) {
        switch (msg.what) {
            case HandlerMainMessages.OperationTimeout: {
                Log.v(TAG, logPrefix("handleMessage: OperationTimeout"));
                pendingGattOperationTimeout(msg);
                break;
            }
            case HandlerMainMessages.SolicitedDisconnectInternalTimeout: {
                Log.v(TAG, logPrefix("handleMessage: SolicitedDisconnectInternalTimeout"));
                Runnable runAfterDisconnect = (Runnable) msg.obj;
                onDeviceDisconnected(mBluetoothGatt, -1, GattHandlerListener.DisconnectReason.SolicitedDisconnectTimeout, false, runAfterDisconnect);
                break;
            }
            case HandlerMainMessages.onCharacteristicChanged: {
                if (ignoreIfIsDisconnectingOrDisconnected("handleMessage: onCharacteristicChanged")) {
                    return false;
                }

                BluetoothGattCharacteristic characteristic = (BluetoothGattCharacteristic) msg.obj;
                if (VERBOSE_LOG_CHARACTERISTIC_CHANGE) {
                    Log.v(TAG, logPrefix("handleMessage: onCharacteristicChanged characteristic=" + characteristic.getUuid()));
                }

                boolean disconnect = false;

                for (GattHandlerListener deviceListener : mListenerManager.beginTraversing()) {
                    disconnect |= deviceListener.onDeviceCharacteristicChanged(GattHandler.this, characteristic);
                }
                mListenerManager.endTraversing();

                Log.v(TAG, logPrefix("handleMessage: onCharacteristicChanged: disconnect=" + disconnect));
                if (disconnect) {
                    disconnect(null);
                }

                break;
            }
        }

        return false;
    }

    @SuppressWarnings("unused")
    public boolean readRemoteRssi() {
        return readRemoteRssi(sDefaultOperationTimeoutMillis, null, null);
    }

    @SuppressWarnings("unused")
    public boolean readRemoteRssi(Runnable runAfterSuccess, Runnable runAfterFail) {
        return readRemoteRssi(sDefaultOperationTimeoutMillis, runAfterSuccess, runAfterFail);
    }

    @SuppressWarnings("WeakerAccess")
    public boolean readRemoteRssi(final long timeoutMillis, final Runnable runAfterSuccess, final Runnable runAfterFail) {
        Log.i(TAG, logPrefix("+readRemoteRssi(timeoutMillis=" + timeoutMillis + ", runAfterSuccess=" + runAfterSuccess + ')'));

        if (!isBluetoothAdapterEnabled("readRemoteRssi")) {
            return false;
        }

        if (ignoreIfIsDisconnectingOrDisconnected("readRemoteRssi")) {
            return false;
        }

        final GattHandlerListener.GattOperation operation = GattHandlerListener.GattOperation.ReadRemoteRssi;

        final long startTimeMillis = timerStart(operation);

        postDelayed(() -> {
            try {
                Log.v(TAG, logPrefix("+readRemoteRssi.run(): timeoutMillis=" + timeoutMillis + ')'));

                BluetoothGatt gatt = pendingGattOperationTimeoutReset("readRemoteRssi");
                if (gatt == null) {
                    onDeviceReadRemoteRssi(-1, false);
                    return;
                }

                if (!gatt.readRemoteRssi()) {
                    Log.e(TAG, logPrefix("readRemoteRssi.run: gatt.readRemoteRssi() failed"));
                    onDeviceReadRemoteRssi(-1, false);
                    return;
                }

                pendingGattOperationTimeoutSchedule(operation, startTimeMillis, timeoutMillis, runAfterSuccess, runAfterFail);
            } finally {
                Log.v(TAG, logPrefix("-readRemoteRssi.run(): timeoutMillis=" + timeoutMillis + ')'));
            }
        });

        return true;
    }

    private void onReadRemoteRssi(@SuppressWarnings("unused") BluetoothGatt gatt, int rssi, int status) {
        Log.v(TAG, logPrefix("onReadRemoteRssi(gatt, rssi=" + rssi + ", status=" + status + ')'));

        logStatusIfNotSuccess("onReadRemoteRssi", status, ", rssi=" + rssi);

        boolean success = status == BluetoothGatt.GATT_SUCCESS;

        onDeviceReadRemoteRssi(rssi, success);
    }

    private void onDeviceReadRemoteRssi(final int rssi,
                                        final boolean success) {
        if (!pendingGattOperationTimeoutSignal()) {
            Log.w(TAG, logPrefix("onDeviceReadRemoteRssi: pendingGattOperationTimeoutSignal() == false; ignoring"));
            return;
        }

        final long elapsedMillis = timerElapsed(GattHandlerListener.GattOperation.ReadRemoteRssi, true);

        postDelayed(() -> {
            boolean disconnect = false;

            for (GattHandlerListener deviceListener : mListenerManager.beginTraversing()) {
                disconnect |= deviceListener.onDeviceReadRemoteRssi(GattHandler.this,
                        rssi,
                        success,
                        elapsedMillis);
            }
            mListenerManager.endTraversing();

            Log.v(TAG, logPrefix("onDeviceReadRemoteRssi: success=" + success + ", disconnect=" + disconnect));
            if (!success || disconnect) {
                disconnect(null);
            }
        });
    }

    //
    //
    //

    private static class PendingGattOperationInfo {
        final GattHandlerListener.GattOperation operation;
        final Long startTimeMillis;
        final Long timeoutMillis;
        final Runnable runAfterSuccess;
        final Runnable runAfterFail;

        private PendingGattOperationInfo(GattHandlerListener.GattOperation operation, Long startTimeMillis, Long timeoutMillis,
                                         Runnable runAfterSuccess, Runnable runAfterFail) {
            this.operation = operation;
            this.startTimeMillis = startTimeMillis;
            this.timeoutMillis = timeoutMillis;
            this.runAfterSuccess = runAfterSuccess;
            this.runAfterFail = runAfterFail;
        }
    }

    private PendingGattOperationInfo mPendingGattOperationInfo;

    private void pendingGattOperationTimeoutSchedule(GattHandlerListener.GattOperation operation, long startTimeMillis, long timeoutMillis,
                                                     Runnable runAfterSuccess, Runnable runAfterFail) {
        if (VERBOSE_LOG_PENDING_OPERATION_TIMEOUT) {
            Log.e(TAG, logPrefix("pendingGattOperationTimeoutSchedule(operation=" + operation +
                    ", startTimeMillis=" + startTimeMillis +
                    ", timeoutMillis=" + timeoutMillis +
                    ", runAfterSuccess=" + runAfterSuccess +
                    ", runAfterFail=" + runAfterFail +
                    ')'));
        }
        mPendingGattOperationInfo = new PendingGattOperationInfo(operation, startTimeMillis, timeoutMillis, runAfterSuccess, runAfterFail);
        mHandlerMain.sendEmptyMessageDelayed(HandlerMainMessages.OperationTimeout, timeoutMillis);
    }

    private PendingGattOperationInfo pendingGattOperationTimeoutCancel() {
        if (VERBOSE_LOG_PENDING_OPERATION_TIMEOUT) {
            Log.e(TAG, logPrefix("pendingGattOperationTimeoutCancel()"));
        }
        mHandlerMain.removeMessages(HandlerMainMessages.OperationTimeout);
        PendingGattOperationInfo pendingGattOperationInfo = mPendingGattOperationInfo;
        mPendingGattOperationInfo = null;
        return pendingGattOperationInfo;
    }

    private BluetoothGatt pendingGattOperationTimeoutReset(String callerName) {
        if (VERBOSE_LOG_PENDING_OPERATION_TIMEOUT) {
            Log.e(TAG, logPrefix("pendingGattOperationTimeoutReset(callerName=" + Utils.Companion.quote(callerName) + ')'));
        }

        pendingGattOperationTimeoutCancel();

        //noinspection UnnecessaryLocalVariable
        BluetoothGatt gatt = getBluetoothGatt(true);
        //if (gatt == null)
        //{
        //    Log.w(TAG, logPrefix(callerName + " pendingGattOperationTimeoutReset: getBluetoothGatt(true) == null; ignoring"));
        //}

        return gatt;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean pendingGattOperationTimeoutSignal() {
        if (VERBOSE_LOG_PENDING_OPERATION_TIMEOUT) {
            Log.e(TAG, logPrefix("pendingGattOperationTimeoutSignal()"));
        }

        PendingGattOperationInfo pendingGattOperationInfo = pendingGattOperationTimeoutCancel();

        if (pendingGattOperationInfo == null) {
            if (VERBOSE_LOG_PENDING_OPERATION_TIMEOUT_UNEXPECTED) {
                Log.e(TAG, logPrefix("pendingGattOperationTimeoutSignal: pendingGattOperationInfo == null; ignoring"));
            }
            return false;
        }

        if (pendingGattOperationInfo.runAfterSuccess != null) {
            Log.v(TAG, logPrefix("pendingGattOperationTimeoutSignal: mHandlerMain.post(pendingGattOperationInfo.runAfterSuccess)"));
            mHandlerMain.post(pendingGattOperationInfo.runAfterSuccess);
        }

        return true;
    }

    private void pendingGattOperationTimeout(Message message) {
        if (VERBOSE_LOG_PENDING_OPERATION_TIMEOUT) {
            Log.e(TAG, logPrefix("pendingGattOperationTimeout(message=" + message + ')'));
        }

        PendingGattOperationInfo pendingGattOperationInfo = reconnectIfConnecting();
        if (pendingGattOperationInfo == null) {
            if (VERBOSE_LOG_PENDING_OPERATION_TIMEOUT_UNEXPECTED) {
                Log.e(TAG, logPrefix("pendingGattOperationTimeoutSignal: pendingGattOperationInfo == null; ignoring"));
            }
            return;
        }

        GattHandlerListener.GattOperation operation = pendingGattOperationInfo.operation;
        long startTimeMillis = pendingGattOperationInfo.startTimeMillis;
        long elapsedMillis = System.currentTimeMillis() - startTimeMillis;

        //noinspection SwitchStatementWithTooFewBranches
        switch (operation) {
            case CharacteristicSetNotification:
                mIsWaitingForCharacteristicSetNotification = false;
                break;
        }

        Log.w(TAG, logPrefix("pendingGattOperationTimeout: operation=" + operation + ", elapsedMillis=" + elapsedMillis + "; *TIMED OUT*"));

        onDeviceOperationTimeout(operation, pendingGattOperationInfo.timeoutMillis, elapsedMillis);

        if (pendingGattOperationInfo.runAfterFail != null) {
            mHandlerMain.post(pendingGattOperationInfo.runAfterFail);
        }
    }
}
