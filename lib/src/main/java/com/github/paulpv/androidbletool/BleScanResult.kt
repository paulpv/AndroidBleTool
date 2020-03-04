package com.github.paulpv.androidbletool

import android.bluetooth.le.ScanResult
import com.github.paulpv.androidbletool.utils.ReflectionUtils
import com.github.paulpv.androidbletool.utils.Utils.TAG
import com.github.paulpv.androidbletool.math.LowPassFilter

@Suppress("EqualsOrHashCode")
class BleScanResult {
    companion object {
        @Suppress("unused")
        private val TAG = TAG(BleScanResult::class.java)
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

    @Suppress("MemberVisibilityCanBePrivate")
    val macAddressLong: Long

    @Suppress("MemberVisibilityCanBePrivate")
    val rssi: Int
        get() = scanResult.rssi

    val rssiSmoothed: Int
        get() = rssiSmoothedCurrent

    private var rssiSmoothedCurrent: Int = 0
    private var rssiSmoothedPrevious: Int = 0

    override fun toString(): String {
        return StringBuilder()
            .append(ReflectionUtils.defaultToString(this))
            .append("{ ")
            .append("rssi=").append(rssi)
            .append(", rssiSmoothed=").append(rssiSmoothed)
            .append(", scanResult=").append(scanResult)
            .append(" }")
            .toString()
    }

    override fun equals(other: Any?): Boolean {
        //Log.e(TAG, "#FLAB ${scanResult.device.address} equals(other=$other)")
        val equals = super.equals(other)
        //Log.e(TAG, "#FLAB ${scanResult.device.address} equals=$equals")
        //val equals = other is BleScanResult && other.macAddressLong == macAddressLong
        return equals
    }

    override fun hashCode(): Int {
        val hashCode = super.hashCode()
        //Log.e(TAG, "#FLAB ${scanResult.device.address} hashCode=$hashCode")
        return hashCode
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