package com.github.paulpv.androidbletool.adapter

import com.github.paulpv.androidbletool.BleScanResult
import com.github.paulpv.androidbletool.ExpiringIterableLongSparseArray
import com.github.paulpv.androidbletool.ReflectionUtils
import com.github.paulpv.androidbletool.Utils

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
        private val TAG = Utils.TAG(DeviceInfo::class.java)

        fun newInstance(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>): DeviceInfo {

            // TODO:(pv) Pull these from a pool of unused items to mitigate trashing memory so much

            val bleScanResult = item.value
            val scanResult = bleScanResult.scanResult
            val device = scanResult.device
            @Suppress("UnnecessaryVariable") val deviceInfo = DeviceInfo(
                device.address,
                device.name,
                bleScanResult.rssi,
                bleScanResult.rssiSmoothed,
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