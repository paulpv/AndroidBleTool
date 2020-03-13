package com.github.paulpv.androidbletool

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import android.os.ParcelUuid
import android.util.Log
import android.util.SparseArray
import com.github.paulpv.androidbletool.collections.ExpiringIterableLongSparseArray
import com.github.paulpv.androidbletool.devices.Triggers.Trigger
import com.github.paulpv.androidbletool.devices.Triggers.TriggerSignalLevelRssi
import com.github.paulpv.androidbletool.devices.pebblebee.PebblebeeDevice
import com.github.paulpv.androidbletool.devices.pebblebee.PebblebeeDeviceFinder2
import com.github.paulpv.androidbletool.gatt.GattUuid
import com.github.paulpv.androidbletool.logging.MyLog
import com.github.paulpv.androidbletool.utils.RuntimeUtils
import com.github.paulpv.androidbletool.utils.Utils
import com.github.paulpv.androidbletool.utils.Utils.TAG
import java.nio.ByteBuffer
import java.util.*

class BleToolParser(
    private val deviceFactory: BleDeviceFactory<*>,
    private val parsers: List<BleDeviceParser>
) {
    companion object {
        private val TAG = TAG(BleToolParser::class.java)

        /**
         * @return the first 4 characters of the macAddress
         */
        fun getMacAddressPrefix(macAddress: String?): String? {
            //          1
            // 12345678901234567
            // 11:22:33:44:55:66
            return if (macAddress != null && macAddress.length == 17) macAddress.substring(0, 5).replace(":", "").toLowerCase(Locale.ROOT) else null
        }
    }

    /**
     * https://www.bluetooth.com/specifications/assigned-numbers/company-Identifiers
     */
    interface BluetoothSigManufacturerIds {
        companion object {
            const val APPLE: Int = 0x004C
        }
    }

    class Configuration {
        private val mDeviceAddressPrefixFilters: MutableSet<String>
        private val mServiceUuids: MutableSet<ParcelUuid>
        private val mDeviceNamesLowerCase: MutableSet<String>

        @Suppress("unused")
        val deviceAddressPrefixFilters: Set<String>
            get() = Collections.unmodifiableSet(mDeviceAddressPrefixFilters)

        @Suppress("unused")
        val serviceUuids: Set<ParcelUuid>
            get() = Collections.unmodifiableSet(mServiceUuids)

        fun addDeviceAddressPrefixFilter(value: Int): Configuration {
            mDeviceAddressPrefixFilters.add(Utils.bytesToHexString(value, 2, true))
            return this
        }

        fun isSupportedDeviceAddressPrefix(bluetoothDevice: BluetoothDevice?): Boolean {
            if (bluetoothDevice == null) {
                return mDeviceAddressPrefixFilters.size == 0
            }
            val bluetoothDeviceAddress = bluetoothDevice.address
            val bluetoothDeviceAddressPrefix = getMacAddressPrefix(bluetoothDeviceAddress)
            return bluetoothDeviceAddressPrefix != null && mDeviceAddressPrefixFilters.contains(bluetoothDeviceAddressPrefix.toLowerCase(Locale.ROOT))
        }

        fun addDeviceName(value: String): Configuration {
            mDeviceNamesLowerCase.add(RuntimeUtils.toNonNullNonEmpty(value, "value").toLowerCase(Locale.ROOT))
            return this
        }

        fun isSupportedDeviceName(deviceName: String?): Boolean {
            return deviceName != null && mDeviceNamesLowerCase.contains(deviceName.toLowerCase(Locale.ROOT))
        }

        fun addServiceUuid(value: GattUuid): Configuration {
            mServiceUuids.add(RuntimeUtils.toNonNull(value, "value").parcelable)
            return this
        }

        fun isSupportedServices(serviceUuids: List<ParcelUuid?>?): Boolean {
            for (serviceUuid in mServiceUuids) {
                if (serviceUuids == null || !serviceUuids.contains(serviceUuid)) {
                    return false
                }
            }
            return true
        }

        init {
            mDeviceAddressPrefixFilters = LinkedHashSet()
            mServiceUuids = LinkedHashSet()
            mDeviceNamesLowerCase = LinkedHashSet()
        }
    }

    abstract class BleDeviceParser(
        private val TAG: String,
        protected val debugModelName: String,
        private val configuration: Configuration
    ) {
        private val debugModelHashTag = "#${debugModelName.toUpperCase(Locale.ROOT)}"

        fun getDeviceNameOrScanRecordName(bluetoothDevice: BluetoothDevice?, scanRecord: ScanRecord?): String? {
            var name: String? = null
            if (bluetoothDevice != null) {
                name = bluetoothDevice.name
            }
            if (Utils.isNullOrEmpty(name)) {
                if (scanRecord != null) {
                    name = scanRecord.deviceName
                }
            }
            return name
        }

        fun isSupportedDeviceAddressPrefix(bluetoothDevice: BluetoothDevice): Boolean {
            return configuration.isSupportedDeviceAddressPrefix(bluetoothDevice)
        }

        fun isSupportedDeviceName(deviceName: String?): Boolean {
            return configuration.isSupportedDeviceName(deviceName)
        }

        fun isSupportedServices(serviceUuids: List<ParcelUuid?>?): Boolean {
            return configuration.isSupportedServices(serviceUuids)
        }

        protected fun log(logLevel: Int, macAddress: String, methodName: String, message: String) {
            @Suppress("NAME_SHADOWING") val message = "$macAddress $methodName: $debugModelHashTag $message"
            when (logLevel) {
                Log.VERBOSE -> Log.v(TAG, message)
                Log.INFO -> Log.i(TAG, message)
                Log.WARN -> Log.w(TAG, message)
                Log.ERROR -> Log.e(TAG, message)
            }
        }

        abstract val modelNumber: Int

        abstract fun parseScan(
            scanRecord: ScanRecord,
            bluetoothDevice: BluetoothDevice,
            serviceUuids: MutableList<ParcelUuid>,
            manufacturerId: Int,
            manufacturerSpecificDataByteBuffer: ByteBuffer,
            triggers: MutableSet<Trigger<*>>
        ): Boolean
    }

    private fun logManufacturerSpecificData(
        logLevel: Int,
        @Suppress("SameParameterValue") tag: String,
        debugInfo: String,
        manufacturerSpecificData: SparseArray<ByteArray>?
    ) {
        if (manufacturerSpecificData != null && manufacturerSpecificData.size() > 0) {
            for (i in 0 until manufacturerSpecificData.size()) {
                val manufacturerId = manufacturerSpecificData.keyAt(i)
                val manufacturerSpecificDataBytes = manufacturerSpecificData.valueAt(i)
                val name = String.format("manufacturerSpecificDataBytes[manufacturerId=0x%04X]", manufacturerId)
                MyLog.logBytes(tag, logLevel, debugInfo, name, manufacturerSpecificDataBytes)
            }
        } else {
            Log.v(tag, "$debugInfo: manufacturerSpecificData=${Utils.toString(manufacturerSpecificData)}")
        }
    }

    fun parseScan(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>): BleDevice? {
        val bleScanResult = item.value
        val scanResult = bleScanResult.scanResult
        val bluetoothDevice = scanResult.device

        val scanRecord = scanResult.scanRecord ?: return null

        var parser: BleDeviceParser? = null
        val triggers = mutableSetOf<Trigger<*>>()
        val it = parsers.iterator()
        while (it.hasNext()) {
            parser = it.next()
            triggers.clear()
            if (parseScan(parser, bluetoothDevice, scanRecord, triggers)) {
                break
            }
            parser = null
        }
        if (parser == null) {
            if (true && BuildConfig.DEBUG) {
                Log.v(TAG, "parseScan: no parser recognized the scanned device; ignoring")
            }
            return null
        }

        // Always ensure rssi trigger
        triggers.add(TriggerSignalLevelRssi(scanResult.rssi))

        if (true && BuildConfig.DEBUG) {
            Log.v(TAG, "parseScan: parser=$parser")
            Log.v(TAG, "parseScan: triggers(" + triggers.size + ")=$triggers")
        }

        val device = deviceFactory.getDevice(bluetoothDevice, parser, triggers)
        if (true && BuildConfig.DEBUG) {
            Log.v(TAG, "parseScan: device=$device")
        }

        if (device is PebblebeeDevice) {
            device.update(triggers)
        }

        return device
    }

    private fun parseScan(
        parser: BleDeviceParser,
        bluetoothDevice: BluetoothDevice,
        scanRecord: ScanRecord,
        triggers: MutableSet<Trigger<*>>
    ): Boolean {
        val bluetoothDeviceMacAddress: String = bluetoothDevice.address

        val debugInfo = "$bluetoothDeviceMacAddress parseScan"

        val serviceUuids = scanRecord.serviceUuids
        val serviceData = scanRecord.serviceData
        val manufacturerSpecificData = scanRecord.manufacturerSpecificData

        for (i in 0 until manufacturerSpecificData.size()) {

            val manufacturerId = manufacturerSpecificData.keyAt(i)
            val manufacturerSpecificDataBytes = manufacturerSpecificData.get(manufacturerId)
            val manufacturerSpecificDataByteBuffer = ByteBuffer.wrap(manufacturerSpecificDataBytes)

            manufacturerSpecificDataByteBuffer.rewind()

            var logVerbose = false
            @Suppress("SimplifyBooleanWithConstants")
            if (true && BuildConfig.DEBUG) {
                logVerbose = logVerbose or (parser is PebblebeeDeviceFinder2.Parser)
            }
            if (logVerbose) {
                Log.e(TAG, "$debugInfo: serviceUuids=$serviceUuids")
                Log.e(TAG, "$debugInfo:  serviceData=$serviceData")
                logManufacturerSpecificData(Log.ERROR, TAG, debugInfo, manufacturerSpecificData)
            }

            if (!parser.parseScan(scanRecord, bluetoothDevice, serviceUuids, manufacturerId, manufacturerSpecificDataByteBuffer, triggers)) {
                continue
            }

            val position = manufacturerSpecificDataByteBuffer.position()
            val length = manufacturerSpecificDataByteBuffer.limit()
            val remaining = length - position
            if (remaining > 0) {
                Log.w(TAG, "$debugInfo: manufacturerSpecificData $remaining unprocessed bytes")
                logManufacturerSpecificData(Log.WARN, TAG, debugInfo, manufacturerSpecificData)
            }

            return true
        }

        return false
    }
}