package com.github.paulpv.androidbletool

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.util.*

class BluetoothUtils {
    companion object {
        private val TAG = Utils.TAG(BluetoothUtils::class.java)

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

        @SuppressLint("ObsoleteSdkInt")
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
                // TODO:(pv) Known to sometimes throw DeadObjectException
                //  https://code.google.com/p/android/issues/detail?id=67272
                //  https://github.com/RadiusNetworks/android-ibeacon-service/issues/16
                bluetoothAdapter != null && bluetoothAdapter.isEnabled
            } catch (e: Exception) {
                Log.w(TAG, "isBluetoothAdapterEnabled: mBluetoothAdapter.isEnabled()", e)
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

        fun gattDeviceAddressToLong(gatt: BluetoothGatt): Long {
            return bluetoothDeviceAddressToLong(gatt.device)
        }

        fun bluetoothDeviceAddressToLong(device: BluetoothDevice): Long {
            return macAddressStringToLong(device.address)
        }

        fun gattDeviceAddressToPrettyString(gatt: BluetoothGatt): String {
            return bluetoothDeviceAddressToPrettyString(gatt.device)
        }

        fun bluetoothDeviceAddressToPrettyString(device: BluetoothDevice): String {
            return macAddressStringToPrettyString(device.address)
        }

        fun getShortDeviceAddressString(deviceAddress: String?): String? {
            @Suppress("NAME_SHADOWING") var deviceAddress = deviceAddress
            if (deviceAddress != null) {
                deviceAddress = macAddressStringToStrippedLowerCaseString(deviceAddress)
                val start = Math.max(deviceAddress.length - 4, 0)
                deviceAddress = deviceAddress.substring(start, deviceAddress.length)
                deviceAddress = deviceAddress.toUpperCase()
            }
            if (Utils.isNullOrEmpty(deviceAddress)) {
                deviceAddress = "null"
            }
            return deviceAddress
        }

        fun getShortDeviceAddressString(deviceAddress: Long): String? {
            return getShortDeviceAddressString(macAddressLongToString(deviceAddress))
        }

        fun macAddressStringToStrippedLowerCaseString(macAddress: String): String {
            return macAddress.replace(":", "").toLowerCase()
        }

        fun macAddressStringToLong(macAddress: String): Long {
            /*
            if (macAddress == null || macAddress.length() != 17)
            {
                throw new IllegalArgumentException("macAddress (" + PbString.quote(macAddress) +
                                                   ") must be of format \"%02X:%02X:%02X:%02X:%02X:%02X\"");
            }
            */
            return java.lang.Long.parseLong(macAddressStringToStrippedLowerCaseString(macAddress), 16)
        }

        fun macAddressStringToPrettyString(macAddress: String): String {
            return macAddressLongToPrettyString(macAddressStringToLong(macAddress))
        }

        fun macAddressLongToPrettyString(macAddress: Long): String {
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

        fun macAddressLongToString(macAddressLong: Long): String {
            return String.format(Locale.US, "%012x", macAddressLong)
        }

        fun bluetoothAdapterStateToString(bluetoothAdapterState: Int): String {
            val name: String = when (bluetoothAdapterState) {
                BluetoothAdapter.STATE_OFF -> "STATE_OFF"
                BluetoothAdapter.STATE_TURNING_ON -> "STATE_TURNING_ON"
                BluetoothAdapter.STATE_ON -> "STATE_ON"
                BluetoothAdapter.STATE_TURNING_OFF -> "STATE_TURNING_OFF"
                else -> "UNKNOWN"
            }
            return "$name($bluetoothAdapterState)"
        }
    }
}