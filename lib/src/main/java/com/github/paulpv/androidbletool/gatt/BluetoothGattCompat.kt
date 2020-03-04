package com.github.paulpv.androidbletool.gatt

import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.Context
import android.os.Build
import android.util.Log
import com.github.paulpv.androidbletool.utils.Utils.TAG
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Original source: (last update: 2019/12/18, last checked: 2020/02/26)
 * https://github.com/Polidea/RxAndroidBle/blob/master/rxandroidble/src/main/java/com/polidea/rxandroidble2/internal/util/BleConnectionCompat.java
 * Apache License 2.0 (with no copyright header at the time this code was copied).
 * https://www.apache.org/licenses/LICENSE-2.0.html
 *
 * Others references/ideas:
 * https://github.com/NordicSemiconductor/Android-BLE-Library/blob/master/ble/src/main/java/no/nordicsemi/android/ble/BleManager.java
 *
 * TODO:(pv) Replace use of Java Reflection with Kotlin Reflection
 *  https://kotlinlang.org/docs/reference/reflection.html
 *  https://www.baeldung.com/kotlin-reflection
 */
class BluetoothGattCompat(private val context: Context) {
    companion object {
        private val TAG = TAG(BluetoothGattCompat::class.java)

        private val iBluetoothManager: Any?
            @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
            get() {
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return null
                val getBluetoothManagerMethod =
                    getMethodFromClass(
                        bluetoothAdapter.javaClass,
                        "getBluetoothManager"
                    )
                return getBluetoothManagerMethod.invoke(bluetoothAdapter)
            }

        @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
        private fun getIBluetoothGatt(iBluetoothManager: Any?): Any? {
            if (iBluetoothManager == null) {
                return null
            }
            val getBluetoothGattMethod = getMethodFromClass(
                iBluetoothManager.javaClass,
                "getBluetoothGatt"
            )
            return getBluetoothGattMethod.invoke(iBluetoothManager)
        }

        @Throws(NoSuchMethodException::class)
        private fun getMethodFromClass(cls: Class<*>, methodName: String): Method {
            val method = cls.getDeclaredMethod(methodName)
            method.isAccessible = true
            return method
        }

        @Throws(NoSuchFieldException::class, IllegalAccessException::class)
        private fun setAutoConnectValue(bluetoothGatt: BluetoothGatt, autoConnect: Boolean) {
            val autoConnectField = bluetoothGatt.javaClass.getDeclaredField("mAutoConnect")
            autoConnectField.isAccessible = true
            autoConnectField.setBoolean(bluetoothGatt, autoConnect)
        }

        /*
        Competitor's "connectUsingReflection"
        try {
            return (BluetoothGatt) bluetoothDevice.getClass().getMethod("connectGatt", new Class[]{Context.class, Boolean.TYPE, BluetoothGattCallback.class, Integer.TYPE}).invoke(bluetoothDevice, new Object[]{this.context, Boolean.valueOf(false), baseBleGattCallback, Integer.valueOf(2)});
        } catch (Throwable e) {
            String str = TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("getBluetoothGatt: Exception ");
            stringBuilder.append(e.getMessage());
            MasterLog.m7120v(str, stringBuilder.toString());
            Crashlytics.logException(e);
            return bluetoothDevice.connectGatt(this.context, false, baseBleGattCallback);
        }
        */
        @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class, NoSuchFieldException::class)
        private fun connectUsingReflection(
            bluetoothGatt: BluetoothGatt,
            bluetoothGattCallback: BluetoothGattCallback,
            autoConnect: Boolean
        ): Boolean {
            Log.v(TAG, "Connecting using reflection")
            setAutoConnectValue(bluetoothGatt, autoConnect)
            val connectMethod = bluetoothGatt.javaClass
                .getDeclaredMethod("connect", Boolean::class.java, BluetoothGattCallback::class.java)
            connectMethod.isAccessible = true
            return connectMethod.invoke(bluetoothGatt, true, bluetoothGattCallback) as Boolean
        }
    }

    fun connectGatt(
        remoteDevice: BluetoothDevice?,
        autoConnect: Boolean,
        bluetoothGattCallback: BluetoothGattCallback
    ): BluetoothGatt? {
        if (remoteDevice == null) {
            return null
        }

        /*
         * Issue that caused a race condition mentioned below was fixed in 7.0.0_r1
         * https://android.googlesource.com/platform/frameworks/base/+/android-7.0.0_r1/core/java/android/bluetooth/BluetoothGatt.java#649
         * compared to
         * https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r72/core/java/android/bluetooth/BluetoothGatt.java#739
         * issue: https://android.googlesource.com/platform/frameworks/base/+/d35167adcaa40cb54df8e392379dfdfe98bcdba2%5E%21/#F0
         */
        if (Build.VERSION.SDK_INT >= 24 || !autoConnect) {
            return connectGattCompat(bluetoothGattCallback, remoteDevice, autoConnect)
        }

        /*
         * Some implementations of Bluetooth Stack have a race condition where autoConnect flag
         * is not properly set *BEFORE* calling connectGatt. That's the reason for using reflection
         * to set the flag manually.
         */
        try {
            Log.v(TAG, "Trying to connectGatt using reflection.")
            val iBluetoothGatt =
                getIBluetoothGatt(iBluetoothManager)
            if (iBluetoothGatt == null) {
                Log.w(TAG, "Couldn't get iBluetoothGatt object")
                return connectGattCompat(bluetoothGattCallback, remoteDevice, true)
            }
            val bluetoothGatt = createBluetoothGatt(iBluetoothGatt, remoteDevice)
            val connectedSuccessfully = connectUsingReflection(
                bluetoothGatt,
                bluetoothGattCallback,
                true
            )
            if (!connectedSuccessfully) {
                Log.w(TAG, "Connection using reflection failed, closing gatt")
                GattUtils.safeClose("connectGatt", bluetoothGatt)
                return null
            }
            return bluetoothGatt
        } catch (exception: Exception) {
            when (exception) {
                is NoSuchMethodException,
                is IllegalAccessException,
                is IllegalArgumentException,
                is InvocationTargetException,
                is InstantiationException,
                is NoSuchFieldException -> {
                    Log.w(TAG, "Error while trying to connect via reflection", exception)
                    return connectGattCompat(bluetoothGattCallback, remoteDevice, true)
                }
                else -> throw exception
            }
        }
    }

    private fun connectGattCompat(
        bluetoothGattCallback: BluetoothGattCallback,
        device: BluetoothDevice,
        autoConnect: Boolean
    ): BluetoothGatt? {
        Log.v(TAG, "Connecting without reflection")
        return if (Build.VERSION.SDK_INT >= 23) {
            device.connectGatt(context, autoConnect, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, autoConnect, bluetoothGattCallback)
        }
    }

    @TargetApi(23)
    @Throws(IllegalAccessException::class, InvocationTargetException::class, InstantiationException::class)
    private fun createBluetoothGatt(
        iBluetoothGatt: Any,
        remoteDevice: BluetoothDevice
    ): BluetoothGatt {
        val bluetoothGattConstructor = BluetoothGatt::class.java.declaredConstructors[0]
        bluetoothGattConstructor.isAccessible = true
        Log.v(TAG, "Found constructor with args count = ${bluetoothGattConstructor.parameterTypes.size}")
        return if (bluetoothGattConstructor.parameterTypes.size == 4) {
            bluetoothGattConstructor.newInstance(context, iBluetoothGatt, remoteDevice, BluetoothDevice.TRANSPORT_LE) as BluetoothGatt
        } else {
            bluetoothGattConstructor.newInstance(context, iBluetoothGatt, remoteDevice) as BluetoothGatt
        }
    }
}
