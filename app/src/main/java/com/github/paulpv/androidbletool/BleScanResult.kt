package com.github.paulpv.androidbletool

import android.bluetooth.le.ScanResult

@Suppress("EqualsOrHashCode")
class BleScanResult {
    companion object {
        @Suppress("unused")
        private val TAG = Utils.TAG(BleScanResult::class.java)
    }

    constructor(bleScanResult: BleScanResult?, scanResult: ScanResult) :
            this(scanResult, bleScanResult?.rssiSmoothedCurrent ?: 0)

    constructor(scanResult: ScanResult, rssiSmoothedCurrent: Int = 0) {
        this.macAddressLong = BluetoothUtils.macAddressStringToLong(scanResult.device.address)
        this.rssiSmoothedCurrent = rssiSmoothedCurrent
        update(scanResult)
    }

    lateinit var scanResult: ScanResult
        private set

    val macAddressLong: Long

    val rssi: Int
        get() = scanResult.rssi

    val rssiSmoothed: Int
        get() = rssiSmoothedCurrent

    private var rssiSmoothedCurrent: Int = 0
    private var rssiSmoothedPrevious: Int = 0

    override fun toString(): String {
        return StringBuilder()
            .append(ReflectionUtils.getShortClassName(this))
            .append("@").append(Integer.toHexString(hashCode()))
            .append("{ ")
            .append(" rssi=").append(rssi)
            .append(", rssiSmoothed=").append(rssiSmoothed)
            .append(", scanResult=").append(scanResult)
            .append(" }")
            .toString()
    }

    override fun equals(other: Any?): Boolean {
        return other is BleScanResult && other.macAddressLong == macAddressLong
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    fun update(scanResult: ScanResult): Boolean {
        this.scanResult = scanResult

        var rssi = scanResult.rssi

        //Log.e(TAG, "#FLAB ${scanResult.bleDevice.macAddress} update: BEFORE rssi=$rssi")
        //Log.e(TAG, "#FLAB ${scanResult.bleDevice.macAddress} update: BEFORE rssiSmoothedCurrent=$rssiSmoothedCurrent")
        //Log.e(TAG, "#FLAB ${scanResult.bleDevice.macAddress} update: BEFORE rssiSmoothedPrevious=$rssiSmoothedPrevious")
        if (rssi != 0) {
            if (rssiSmoothedCurrent != 0) {
                rssi = LowPassFilter.update(rssi.toLong(), rssiSmoothedCurrent.toLong()).toInt()
            }
        }
        rssiSmoothedPrevious = rssiSmoothedCurrent
        val changed = rssiSmoothedPrevious != rssi
        rssiSmoothedCurrent = rssi

        //Log.e(TAG, "#FLAB ${scanResult.bleDevice.macAddress} update: AFTER rssi=$rssi")
        //Log.e(TAG, "#FLAB ${scanResult.bleDevice.macAddress} update: AFTER rssiSmoothedCurrent=$rssiSmoothedCurrent")
        //Log.e(TAG, "#FLAB ${scanResult.bleDevice.macAddress} update: AFTER rssiSmoothedPrevious=$rssiSmoothedPrevious")

        return changed
    }
}