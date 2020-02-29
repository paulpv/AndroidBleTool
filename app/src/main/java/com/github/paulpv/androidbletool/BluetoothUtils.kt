package com.github.paulpv.androidbletool

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.github.paulpv.androidbletool.utils.Utils
import java.util.*

object BluetoothUtils {
    private val TAG = Utils.TAG(BluetoothUtils::class.java)

    @Suppress("MemberVisibilityCanBePrivate")
    fun isBluetoothSupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
    }

    fun isBluetoothLowEnergySupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    /**
     * @param context
     * @return null if Bluetooth is not supported
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun getBluetoothManager(context: Context): BluetoothManager? {
        return if (!isBluetoothSupported(context)) null else context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    }

    /**
     * Per: http://developer.android.com/reference/android/bluetooth/BluetoothAdapter.html
     * "To get a BluetoothAdapter representing the local Bluetooth adapter, when running on JELLY_BEAN_MR1 and below,
     * call the static getDefaultAdapter() method; when running on JELLY_BEAN_MR2 and higher, retrieve it through
     * getSystemService(String) with BLUETOOTH_SERVICE. Fundamentally, this is your starting point for all Bluetooth
     * actions."
     *
     * @return null if Bluetooth is not supported
     */
    @SuppressLint("ObsoleteSdkInt")
    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        if (!isBluetoothSupported(context)) {
            return null
        }
        return if (Build.VERSION.SDK_INT <= 17) {
            BluetoothAdapter.getDefaultAdapter()
        } else {
            getBluetoothManager(context)!!.adapter
        }
    }

    @Suppress("unused")
    fun getBluetoothLeAdvertiser(bluetoothAdapter: BluetoothAdapter?): BluetoothLeAdvertiser? {
        var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null

        if (Build.VERSION.SDK_INT >= 21 && bluetoothAdapter != null) {
            try {
                if (bluetoothAdapter.isMultipleAdvertisementSupported) {
                    bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
                }
            } catch (e: NullPointerException) {
                // Fixes https://console.firebase.google.com/project/pebblebee-finder/monitoring/app/android:com.pebblebee.app.hive3/cluster/bf7672a5
                //  java.lang.NullPointerException: Attempt to invoke interface method 'boolean android.bluetooth.IBluetooth.isMultiAdvertisementSupported()' on a null object reference
                //  android.bluetooth.BluetoothAdapter.isMultipleAdvertisementSupported (BluetoothAdapter.java:1201)
                //  com.pebblebee.common.bluetooth.PbBluetoothUtils.getBluetoothLeAdvertiser (SourceFile:106)
                bluetoothLeAdvertiser = null
            }
        }
        return bluetoothLeAdvertiser
    }

    fun isBluetoothAdapterEnabled(bluetoothAdapter: BluetoothAdapter?): Boolean {
        return try {
            // NOTE:(pv) Known to sometimes throw DeadObjectException
            //  https://code.google.com/p/android/issues/detail?id=67272
            //  https://github.com/RadiusNetworks/android-ibeacon-service/issues/16
            bluetoothAdapter != null && bluetoothAdapter.isEnabled
        } catch (e: Exception) {
            Log.w(TAG, "isBluetoothAdapterEnabled: bluetoothAdapter.isEnabled()", e)
            false
        }
    }

    /**
     * @param bluetoothAdapter
     * @param on
     * @return true if successfully set; false if the set failed
     * @see <ul>
     * <li><a href="https://code.google.com/p/android/issues/detail?id=67272">https://code.google.com/p/android/issues/detail?id=67272</a></li>
     * <li><a href="https://github.com/RadiusNetworks/android-ibeacon-service/issues/16">https://github.com/RadiusNetworks/android-ibeacon-service/issues/16</a></li>
     * </ul>
     */
    @Suppress("unused")
    fun bluetoothAdapterEnable(bluetoothAdapter: BluetoothAdapter?, on: Boolean): Boolean {
        // TODO:(pv) Known to sometimes throw DeadObjectException
        //  https://code.google.com/p/android/issues/detail?id=67272
        //  https://github.com/RadiusNetworks/android-ibeacon-service/issues/16
        return bluetoothAdapter != null &&
                if (on) {
                    try {
                        bluetoothAdapter.enable()
                        true
                    } catch (e: Exception) {
                        Log.v(TAG, "bluetoothAdapterEnable: bluetoothAdapter.enable()", e)
                        false
                    }
                } else {
                    try {
                        bluetoothAdapter.disable()
                        true
                    } catch (e: Exception) {
                        Log.v(TAG, "bluetoothAdapterEnable: bluetoothAdapter.disable()", e)
                        false
                    }
                }
    }

    //
    //
    //

    fun throwExceptionIfInvalidBluetoothAddress(deviceAddress: Long) {
        if (deviceAddress == 0L || deviceAddress == -1L) {
            throw IllegalArgumentException("deviceAddress invalid")
        }
    }

    @Suppress("unused")
    fun gattDeviceAddressToLong(gatt: BluetoothGatt?): Long {
        return bluetoothDeviceAddressToLong(gatt?.device)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun bluetoothDeviceAddressToLong(device: BluetoothDevice?): Long {
        return macAddressStringToLong(device?.address)
    }

    fun gattDeviceAddressToString(gatt: BluetoothGatt?): String {
        return bluetoothDeviceAddressToString(gatt?.device)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun bluetoothDeviceAddressToString(device: BluetoothDevice?): String {
        return macAddressStringToString(device?.address)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun getShortDeviceAddressString(deviceAddress: String?): String? {
        @Suppress("NAME_SHADOWING") var deviceAddress = deviceAddress
        if (deviceAddress != null) {
            deviceAddress = macAddressStringToStrippedLowerCaseString(deviceAddress)
            val start = (deviceAddress.length - 4).coerceAtLeast(0)
            deviceAddress = deviceAddress.substring(start, deviceAddress.length)
            deviceAddress = deviceAddress.toUpperCase(Locale.ROOT)
        }
        if (Utils.isNullOrEmpty(deviceAddress)) {
            deviceAddress = "null"
        }
        return deviceAddress
    }

    @Suppress("unused")
    fun getShortDeviceAddressString(deviceAddress: Long): String? {
        return getShortDeviceAddressString(macAddressLongToString(deviceAddress))
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun macAddressStringToStrippedLowerCaseString(macAddress: String?): String {
        return (macAddress?.replace(":", "") ?: "00:00:00:00:00:00").toLowerCase(Locale.ROOT)
    }

    fun macAddressStringToLong(macAddress: String?): Long {
        /*
        if (macAddress == null || macAddress.length() != 17) {
            throw new IllegalArgumentException("macAddress (" + PbString.quote(macAddress) + ") must be of format \"%02X:%02X:%02X:%02X:%02X:%02X\"");
        }
        */
        @Suppress("NAME_SHADOWING") val macAddress = macAddressStringToStrippedLowerCaseString(macAddress)
        return java.lang.Long.parseLong(macAddress, 16)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun macAddressStringToString(macAddress: String?): String {
        return macAddressLongToString(macAddressStringToLong(macAddress))
    }

    /**
     * Per [BluetoothAdapter.getRemoteDevice]:
     * "Valid Bluetooth hardware addresses must be upper case, in a format
     * such as "00:11:22:33:AA:BB"."
     */
    fun macAddressLongToString(macAddress: Long): String {
        return String.format(
            Locale.US, "%02X:%02X:%02X:%02X:%02X:%02X",
            (macAddress shr 40 and 0xff).toByte(),
            (macAddress shr 32 and 0xff).toByte(),
            (macAddress shr 24 and 0xff).toByte(),
            (macAddress shr 16 and 0xff).toByte(),
            (macAddress shr 8 and 0xff).toByte(),
            (macAddress shr 0 and 0xff).toByte()
        )
    }

    //
    //
    //

    fun bluetoothAdapterStateToString(bluetoothAdapterState: Int): String {
        val name = when (bluetoothAdapterState) {
            BluetoothAdapter.STATE_OFF -> "STATE_OFF"
            BluetoothAdapter.STATE_TURNING_ON -> "STATE_TURNING_ON"
            BluetoothAdapter.STATE_ON -> "STATE_ON"
            BluetoothAdapter.STATE_TURNING_OFF -> "STATE_TURNING_OFF"
            else -> "UNKNOWN"
        }
        return "$name($bluetoothAdapterState)"
    }

    fun bluetoothProfileStateToString(state: Int): String? {
        val text = when (state) {
            BluetoothProfile.STATE_DISCONNECTED -> "STATE_DISCONNECTED"
            BluetoothProfile.STATE_DISCONNECTING -> "STATE_DISCONNECTING"
            BluetoothProfile.STATE_CONNECTING -> "STATE_CONNECTING"
            BluetoothProfile.STATE_CONNECTED -> "STATE_CONNECTED"
            else -> "STATE_UNKNOWN"
        }
        return "$text($state)"
    }

    fun callbackTypeToString(callbackType: Int): String {
        val text = when (callbackType) {
            ScanSettings.CALLBACK_TYPE_FIRST_MATCH -> "CALLBACK_TYPE_FIRST_MATCH"
            ScanSettings.CALLBACK_TYPE_MATCH_LOST -> "CALLBACK_TYPE_MATCH_LOST"
            ScanSettings.CALLBACK_TYPE_ALL_MATCHES -> "CALLBACK_TYPE_ALL_MATCHES"
            else -> "CALLBACK_TYPE_UNKNOWN"
        }
        return "$text($callbackType)"
    }

    //
    //
    //

    /**
     * From hidden @see ScanCallback#SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES
     */
    @Suppress("MemberVisibilityCanBePrivate")
    const val SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES = 5

    /**
     * From hidden @see ScanCallback#SCAN_FAILED_SCANNING_TOO_FREQUENTLY
     */
    @Suppress("MemberVisibilityCanBePrivate")
    const val SCAN_FAILED_SCANNING_TOO_FREQUENTLY = 6

    fun scanErrorCodeToString(errorCode: Int): String {
        val message = when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "SCAN_FAILED_ALREADY_STARTED"
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED"
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "SCAN_FAILED_INTERNAL_ERROR"
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "SCAN_FAILED_FEATURE_UNSUPPORTED"
            SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES"
            SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "SCAN_FAILED_SCANNING_TOO_FREQUENTLY"
            else -> "SCAN_FAILED_UNKNOWN"
        }
        return "$message($errorCode)"
    }

    class BleScanThrowable(@Suppress("MemberVisibilityCanBePrivate", "CanBeParameter") val errorCode: Int) :
        Throwable(scanErrorCodeToString(errorCode))
}