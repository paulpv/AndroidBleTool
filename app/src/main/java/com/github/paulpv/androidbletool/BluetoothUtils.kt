package com.github.paulpv.androidbletool

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

class BluetoothUtils {
    companion object {
        private const val TAG = "BluetoothUtils"

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
    }
}