package com.github.paulpv.testapp.adapter

import android.bluetooth.le.ScanResult
import android.util.Log
import com.github.paulpv.androidbletool.BleScanResult
import com.github.paulpv.androidbletool.BuildConfig
import com.github.paulpv.androidbletool.collections.ExpiringIterableLongSparseArray
import com.github.paulpv.androidbletool.utils.ReflectionUtils
import com.github.paulpv.androidbletool.utils.Utils.TAG

data class DeviceInfo(
    val macAddress: String,
    val name: String,
    val signalStrengthRealtime: Int,
    val signalStrengthSmoothed: Int,
    val addedElapsedMillis: Long,
    val lastUpdatedElapsedMillis: Long,
    val timeoutRemainingMillis: Long
) {
    companion object {
        private val TAG = TAG(DeviceInfo::class.java)

        //private val deviceInfoPool = ArrayQueue<DeviceInfo>("DeviceInfoPool")

        /**
         * NOTE:(pv) Is is possible for device.name to return null even though the scan says otherwise
         * @return scanResult.device.name or scanResult.scanRecord.deviceName or device.address
         */
        fun getDeviceName(scanResult: ScanResult): String {
            val device = scanResult.device
            var deviceName: String? = device.name
            if (deviceName != null) {
                return deviceName
            }
            Log.w(TAG, "getDeviceName: UNEXPECTED scanResult.device.name == null")
            val scanRecord = scanResult.scanRecord
            if (scanRecord != null) {
                deviceName = scanRecord.deviceName
                if (deviceName != null) {
                    return deviceName
                }
                Log.w(TAG, "getDeviceName: UNEXPECTED scanResult.scanRecord.deviceName == null")
            } else {
                Log.w(TAG, "getDeviceName: UNEXPECTED scanResult.scanRecord == null")
            }
            deviceName = "{${device.address}}"
            return deviceName
        }

        fun newInstance(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>): DeviceInfo {

            // TODO:(pv) Pull these from a pool of unused items to mitigate trashing memory so much
            //  This requires identifying in the code a place where the item will no longer be used

            val bleScanResult = item.value
            val scanResult = bleScanResult.scanResult
            val device = scanResult.device

            val address: String
            val name: String
            val rssi: Int
            val rssiSmoothed: Int

            @Suppress("SimplifyBooleanWithConstants")
            if (false && BuildConfig.DEBUG) {
                when (device.address) {
                    "0E:06:E5:75:F0:AE" -> {
                        address = "A Black #1"
                        //name = "C Black #1"
                        name = "CARD"
                        rssi = -42
                        rssiSmoothed = rssi
                    }
                    "0E:06:E5:E6:E7:AE" -> {
                        address = "B Green"
                        //name = "A Green"
                        name = "FNDR"
                        //rssi = -42
                        rssi = -24
                        rssiSmoothed = rssi
                    }
                    "0E:06:E5:E2:73:AF" -> {
                        address = "C Black #2"
                        //name = "B Black #2"
                        //name = "FNDR"
                        name = "CARD"
                        rssi = -42
                        rssiSmoothed = rssi
                    }
                    else -> {
                        address = device.address
                        name = device.name
                        rssi = bleScanResult.rssi
                        rssiSmoothed = bleScanResult.rssiSmoothed
                    }
                }
            } else {
                address = device.address
                name = getDeviceName(scanResult)
                rssi = bleScanResult.rssi
                rssiSmoothed = bleScanResult.rssiSmoothed
            }


            @Suppress("UnnecessaryVariable") val deviceInfo = DeviceInfo(
                address,
                name,
                rssi,
                rssiSmoothed,
                item.addedElapsedMillis,
                item.lastUpdatedElapsedMillis,
                item.timeoutRemainingMillis
            )
            return deviceInfo
        }
    }

    /**
     * The default Kotlin impl does not show the hashCode address; this one does.
     */
    override fun toString(): String {
        return ReflectionUtils.getShortClassName(this) + "@" + Integer.toHexString(hashCode()) + "(" +
                "macAddress=$macAddress" +
                ", name=$name" +
                ", signalStrengthRealtime=$signalStrengthRealtime" +
                ", signalStrengthSmoothed=$signalStrengthSmoothed" +
                ", addedElapsedMillis=$addedElapsedMillis" +
                ", lastUpdatedElapsedMillis=$lastUpdatedElapsedMillis" +
                ", timeoutRemainingMillis=$timeoutRemainingMillis" +
                ")"
    }
}