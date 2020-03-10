package com.github.paulpv.androidbletool

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import android.os.ParcelUuid
import android.util.Log
import android.util.SparseArray
import com.github.paulpv.androidbletool.collections.ExpiringIterableLongSparseArray
import com.github.paulpv.androidbletool.devices.PbBleDeviceFinder2
import com.github.paulpv.androidbletool.devices.Trigger
import com.github.paulpv.androidbletool.gatt.GattUuid
import com.github.paulpv.androidbletool.logging.MyLog
import com.github.paulpv.androidbletool.utils.RuntimeUtils
import com.github.paulpv.androidbletool.utils.Utils
import com.github.paulpv.androidbletool.utils.Utils.TAG
import java.nio.ByteBuffer
import java.util.*

class BleToolParser(private val parsers: List<AbstractParser>) {
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

    /**
     * NOTE that these are the reverse of [PebblebeeMacAddressPrefix]
     */
    interface PebblebeeManufacturerIds {
        companion object {
            const val PEBBLEBEE_HONEY_DRAGON_HORNET: Int = 0x0A0E
            const val PETHUB_SIGNAL: Int = 0x0B0E
            const val PEBBLEBEE_STONE: Int = 0x0C0E
            const val PEBBLEBEE_FINDER1: Int = 0x0E0E
            const val PEBBLEBEE_FINDER2: Int = 0x060E
            const val PEBBLEBEE_BUZZER1: Int = 0x0F0E // Buzzer1/Nock
            const val PEBBLEBEE_BUZZER2: Int = 0x100E // Buzzer2/LocationMarker
            const val PEBBLEBEE_CARD: Int = 0x050E
            val ALL = Collections.unmodifiableSet(
                HashSet(
                    listOf(
                        PEBBLEBEE_HONEY_DRAGON_HORNET,
                        PETHUB_SIGNAL,
                        PEBBLEBEE_STONE,
                        PEBBLEBEE_FINDER1,
                        PEBBLEBEE_FINDER2,
                        PEBBLEBEE_BUZZER1,
                        PEBBLEBEE_BUZZER2,
                        PEBBLEBEE_CARD
                    )
                )
            )
        }
    }

    /**
     * NOTE that these are the reverse of [PebblebeeManufacturerIds]
     */
    interface PebblebeeMacAddressPrefix {
        companion object {
            const val PEBBLEBEE_HONEY_DRAGON_HORNET: Int = 0x0E0A
            const val PEBBLEBEE_HONEY_DRAGON_HORNET_STRING = "0e0a"
            const val PETHUB_SIGNAL: Int = 0x0E0B
            const val PETHUB_SIGNAL_STRING = "0e0b"
            const val PEBBLEBEE_STONE: Int = 0x0E0C
            const val PEBBLEBEE_STONE_STRING = "0e0c"
            const val PEBBLEBEE_FINDER1: Int = 0x0E0E
            const val PEBBLEBEE_FINDER1_STRING = "0e0e"
            const val PEBBLEBEE_FINDER2: Int = 0x0E06
            const val PEBBLEBEE_FINDER2_STRING = "0e06"
            const val PEBBLEBEE_BUZZER1: Int = 0x0E0F // Buzzer1/Nock
            const val PEBBLEBEE_BUZZER1_STRING = "0e0f"
            const val PEBBLEBEE_BUZZER2: Int = 0x0E10 // Buzzer2/LocationMarker
            const val PEBBLEBEE_BUZZER2_STRING = "0e10"
            const val PEBBLEBEE_CARD: Int = 0x0E05
            const val PEBBLEBEE_CARD_STRING = "0e05"
        }
    }

    internal interface PebblebeeDeviceCaseSensitiveName {
        companion object {
            const val PEBBLEBEE_HONEY = "PebbleBee" // Honey
            const val PEBBLEBEE_DRAGON_HORNET = "Pebblebee" // Dragon/Hornet
            const val PETHUB_SIGNAL = "SIGNAL" // Pethub Signal
            const val FINDER = "FNDR" // Finder
            const val BUZZER1_0_0 = "smartnock" // Buzzer1/Nock
            const val BUZZER1_0_1 = "snck" // Buzzer1/Nock
            const val BUZZER2 = "BCMK" // Buzzer2/LocationMarker
            const val CARD = "CARD" // Black Card
            const val FOUND = "FND" // Found
            const val LUMA = "LUMA" // Luma
            val ALL = Collections.unmodifiableSet(
                HashSet(
                    listOf(
                        PEBBLEBEE_HONEY,
                        PEBBLEBEE_DRAGON_HORNET,
                        PETHUB_SIGNAL,
                        FINDER,
                        BUZZER1_0_0,
                        BUZZER1_0_1,
                        BUZZER2,
                        CARD,
                        FOUND,
                        LUMA
                    )
                )
            )
        }
    }

    interface Regions {
        companion object {
            /**
             * Normal UUID for ranging, turned off when button held for 10 secs (button only mode)
             */
            const val TRACKING_STONE = "d149cb95-f212-4a20-8a17-e3a2f508c1ff"
            const val TRACKING_FINDER = "d149cb95-f212-4a20-8a17-e3a2f508c1aa"

            /**
             * Transmits after a single button or hold button (3 secs then release), currently transmits fast broadcast 2S.
             * Following the Interrupt period the Data for 2S.
             * Then reverts to Tracking UUID (unless off).
             */
            const val INTERRUPT = "d149cb95-f212-4a20-8a17-e3a2f508c1cc"

            /**
             * Transmits following a motion for 10S, if it keeps moving it will keep TX's.
             * Then reverts to Tracking UUID (unless off)
             */
            const val MOTION = "d149cb95-f212-4a20-8a17-e3a2f508c1ee"
        }
    }

    object AdvertisementSpeed {
        const val FAST: Byte = 0
        const val SLOW: Byte = 1
        fun toString(value: Byte): String {
            val s: String = when (value) {
                FAST -> "FAST"
                SLOW -> "SLOW"
                else -> "UNKNOWN"
            }
            return "$s($value)"
        }
    }


    object Actions {
        const val NONE: Byte = 0
        const val CLICK_SHORT: Byte = 1
        const val CLICK_LONG: Byte = 2
        const val CLICK_DOUBLE: Byte = 3
        fun toString(value: Byte): String {
            val s: String = when (value) {
                NONE -> "NONE"
                CLICK_SHORT -> "CLICK_SHORT"
                CLICK_LONG -> "CLICK_LONG"
                CLICK_DOUBLE -> "CLICK_DOUBLE"
                else -> "UNKNOWN"
            }
            return "$s($value)"
        }
    }

    object ActionSequence {
        const val NeverPressed: Byte = 0
        const val JustPressed: Byte = 1
        const val Resetting: Byte = 2
        const val PressedBefore: Byte = 4
        fun toString(value: Byte): String {
            val s: String = when (value) {
                NeverPressed -> "NeverPressed"
                JustPressed -> "JustPressed"
                Resetting -> "Resetting"
                PressedBefore -> "PressedBefore"
                else -> "UNKNOWN"
            }
            return "$s($value)"
        }
    }

    class Configuration {
        private val mDeviceAddressPrefixFilters: MutableSet<String>
        private val mServiceUuids: MutableSet<ParcelUuid>
        private val mDeviceNamesLowerCase: MutableSet<String>
        val deviceAddressPrefixFilters: Set<String>
            get() = Collections.unmodifiableSet(mDeviceAddressPrefixFilters)

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

    abstract class AbstractParser(
        private val TAG: String,
        protected val debugModelName: String,
        private val configuration: Configuration
    ) {

        private val debugModelHashTag = "#${debugModelName.toUpperCase(Locale.ROOT)}"

        open fun getDeviceNameOrScanRecordName(bluetoothDevice: BluetoothDevice?, scanRecord: ScanRecord?): String? {
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

        abstract fun parseScan(
            scanRecord: ScanRecord,
            bluetoothDevice: BluetoothDevice,
            serviceUuids: MutableList<ParcelUuid>,
            manufacturerId: Int,
            manufacturerSpecificDataByteBuffer: ByteBuffer
        ): Set<Trigger<*>>?
    }

    private fun logManufacturerSpecificData(logLevel: Int, tag: String, debugInfo: String, manufacturerSpecificData: SparseArray<ByteArray>?) {
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

    fun parseScan(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>): Set<Trigger<*>>? {
        val bleScanResult = item.value
        val scanResult = bleScanResult.scanResult
        val bluetoothDevice = scanResult.device

        val bluetoothDeviceMacAddress: String = bluetoothDevice.address

        val debugInfo = "$bluetoothDeviceMacAddress parseScan"

        val scanRecord = scanResult.scanRecord ?: return null

        val serviceUuids = scanRecord.serviceUuids
        val serviceData = scanRecord.serviceData
        val manufacturerSpecificData = scanRecord.manufacturerSpecificData

        for (i in 0 until manufacturerSpecificData.size()) {

            val manufacturerId = manufacturerSpecificData.keyAt(i)
            val manufacturerSpecificDataBytes = manufacturerSpecificData.get(manufacturerId)
            val manufacturerSpecificDataByteBuffer = ByteBuffer.wrap(manufacturerSpecificDataBytes)

            for (parser: AbstractParser in parsers) {

                manufacturerSpecificDataByteBuffer.rewind()

                var logVerbose = false
                @Suppress("SimplifyBooleanWithConstants")
                if (false && BuildConfig.DEBUG) {
                    logVerbose = logVerbose or (parser is PbBleDeviceFinder2.Parser)
                }
                if (logVerbose) {
                    Log.e(TAG, "$debugInfo: serviceUuids=$serviceUuids")
                    Log.e(TAG, "$debugInfo: serviceData=$serviceData")
                    logManufacturerSpecificData(Log.ERROR, TAG, debugInfo, manufacturerSpecificData)
                }

                val triggers = parser.parseScan(scanRecord, bluetoothDevice, serviceUuids, manufacturerId, manufacturerSpecificDataByteBuffer)
                if (logVerbose) {
                    Log.e(TAG, "$debugInfo: triggers=$triggers")
                }
                if (triggers == null) {
                    continue
                }

                val position = manufacturerSpecificDataByteBuffer.position()
                val length = manufacturerSpecificDataByteBuffer.limit()
                val remaining = length - position
                if (remaining > 0) {
                    Log.w(TAG, "$debugInfo: manufacturerSpecificData $remaining unprocessed bytes")
                    logManufacturerSpecificData(Log.WARN, TAG, debugInfo, manufacturerSpecificData)
                }

                return triggers
            }
        }

        return null
    }
}