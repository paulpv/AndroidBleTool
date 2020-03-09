package com.github.paulpv.androidbletool.devices

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import android.os.ParcelUuid
import android.util.Log
import com.github.paulpv.androidbletool.BleDevice
import com.github.paulpv.androidbletool.BleToolParser
import com.github.paulpv.androidbletool.BluetoothUtils
import com.github.paulpv.androidbletool.BuildConfig
import com.github.paulpv.androidbletool.gatt.GattHandler
import com.github.paulpv.androidbletool.gatt.GattUuids
import com.github.paulpv.androidbletool.utils.Utils
import com.github.paulpv.androidbletool.utils.Utils.TAG
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

object PbBleDeviceFinder2 {
    private val TAG = TAG(PbBleDeviceFinder2::class.java)

    private val PLAY_JINGLE_COUNT_1 = byteArrayOf(0x01, 0x00)
    private val PLAY_JINGLE_COUNT_2 = byteArrayOf(0x01, 0x00, 0x00)
    private val PLAY_JINGLE_COUNT_3 = byteArrayOf(0x01, 0x00, 0x00, 0x00)
    private val PLAY_JINGLE_COUNT_4 = byteArrayOf(0x80.toByte(), 0x01)

    @JvmStatic
    fun requestBeep(bleDevice: BleDevice) {
        val gattHandler = bleDevice.gattHandler

        val runDisconnect = Runnable {
            Log.e(TAG, "DISCONNECTING")
            gattHandler.disconnect(runAfterDisconnect = Runnable {
                Log.e(TAG, "DISCONNECTED!")
            })
        }
        val runBeep = Runnable {
            val service = GattUuids.PEBBLEBEE_FINDER_SERVICE.uuid
            val characteristic = GattUuids.PEBBLEBEE_FINDER_CHARACTERISTIC1.uuid
            val value = PLAY_JINGLE_COUNT_4
            Log.e(TAG, "WRITING")
            if (!gattHandler.characteristicWrite(
                    serviceUuid = service,
                    characteristicUuid = characteristic,
                    value = value,
                    characteristicWriteType = GattHandler.CharacteristicWriteType.DefaultWithResponse,
                    runAfterSuccess = Runnable {
                        Log.e(TAG, "WRITE SUCCESS!")
                        runDisconnect.run()
                    },
                    runAfterFail = Runnable {
                        Log.e(TAG, "WRITE FAIL!")
                        runDisconnect.run()
                    }
                )
            ) {
                runDisconnect.run()
            }
        }

        if (gattHandler.isConnectingOrConnectedAndNotDisconnecting) {
            runBeep.run()
        } else {
            Log.e(TAG, "CONNECTING")
            if (!gattHandler.connect(runAfterConnect = Runnable {
                    Log.e(TAG, "CONNECT SUCCESS!")
                    runBeep.run()
                }, runAfterFail = Runnable {
                    Log.e(TAG, "CONNECT FAIL!")
                    runDisconnect.run()
                })) {
                runDisconnect.run()
            }
        }
    }

    class Parser : BleToolParser.AbstractParser(
        TAG, "Finder2", BleToolParser.Configuration()
            .addDeviceAddressPrefixFilter(BleToolParser.PebblebeeMacAddressPrefix.PEBBLEBEE_FINDER2)
            .addDeviceName(BleToolParser.PebblebeeDeviceCaseSensitiveName.FINDER)
            .addServiceUuid(GattUuids.PEBBLEBEE_FINDER_SERVICE)
    ) {

        private val LOG_IGNORED_MAC_ADDRESS = false && BuildConfig.DEBUG
        private val LOG_IBEACON_VERBOSE = false && BuildConfig.DEBUG
        private val LOG_REGION = false && BuildConfig.DEBUG
        private val LOG_DATA = false && BuildConfig.DEBUG
        private val LOG_DATA_VERBOSE = false && BuildConfig.DEBUG

        override fun parseScan(
            scanRecord: ScanRecord,
            bluetoothDevice: BluetoothDevice,
            serviceUuids: MutableList<ParcelUuid>,
            manufacturerId: Int,
            manufacturerSpecificDataByteBuffer: ByteBuffer
        ): Set<BleToolParser.Trigger<*>>? {

            val bluetoothDeviceAddress = bluetoothDevice.address

            if (!isSupportedDeviceAddressPrefix(bluetoothDevice)) {
                if (LOG_IGNORED_MAC_ADDRESS) {
                    val bluetoothDeviceAddressPrefix = BleToolParser.getMacAddressPrefix(bluetoothDeviceAddress)
                    //@formatter:off
                    log(Log.VERBOSE, bluetoothDeviceAddress, "parse", " Non-$debugModelName macAddress; bluetoothDeviceAddress=${Utils.quote(bluetoothDeviceAddress)}; bluetoothDeviceAddressPrefix=${Utils.quote(bluetoothDeviceAddressPrefix)}; ignoring")
                    //@formatter:on
                }
                return null
            }

            when (manufacturerId) {
                BleToolParser.PebblebeeManufacturerIds.PEBBLEBEE_FINDER2 -> {
                    val bluetoothDeviceName = getDeviceNameOrScanRecordName(bluetoothDevice, scanRecord)
                    if (LOG_DATA_VERBOSE) {
                        log(Log.INFO, bluetoothDeviceAddress, "parse", "bluetoothDeviceName=${Utils.quote(bluetoothDeviceName)}")
                    }
                    if (!isSupportedDeviceName(bluetoothDeviceName)) {
                        //if (!callbacks.isWhitelisted(bluetoothDeviceName, bluetoothDeviceAddress)) {
                        if (LOG_DATA_VERBOSE) {
                            //@formatter:off
                            log(Log.VERBOSE, bluetoothDeviceAddress, "parse", "Non-$debugModelName name; bluetoothDeviceAddress=${Utils.quote(bluetoothDeviceAddress)}; bluetoothDeviceName=${Utils.quote(bluetoothDeviceName)}; ignoring")
                            //@formatter:on
                        }
                        return null
                        //}
                    }
                    if (!isSupportedServices(serviceUuids)) {
                        if (LOG_DATA_VERBOSE) {
                            //@formatter:off
                            log(Log.VERBOSE, bluetoothDeviceAddress, "parse", "mServiceUuids != serviceUuids; bluetoothDeviceAddress=${Utils.quote(bluetoothDeviceAddress)}; ignoring")
                            //@formatter:on
                        }
                        return null
                    }

                    //
                    // Process as 2
                    //
                    if (LOG_DATA) {
                        log(Log.INFO, bluetoothDeviceAddress, "parse", "DATA")
                    }
                    val originalByteOrder = manufacturerSpecificDataByteBuffer.order()
                    return try {
                        if (LOG_DATA_VERBOSE) {
                            log(Log.INFO, bluetoothDeviceAddress, "parse", "DATA -- BEGIN --------")
                        }
                        manufacturerSpecificDataByteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                        val macAddress = String.format(
                            Locale.US, "%02X:%02X:%02X:%02X:%02X:%02X",
                            (manufacturerId shr 0 and 0xff).toByte(),
                            (manufacturerId shr 8 and 0xff).toByte(),
                            manufacturerSpecificDataByteBuffer.get(),
                            manufacturerSpecificDataByteBuffer.get(),
                            manufacturerSpecificDataByteBuffer.get(),
                            manufacturerSpecificDataByteBuffer.get()
                        )
                        if (LOG_DATA_VERBOSE) {
                            log(Log.INFO, bluetoothDeviceAddress, "parse", "DATA macAddress=${Utils.quote(macAddress)}")
                        }
                        if (macAddress == "00:00:00:00:00:00") {
                            // TODO:(pv) Report to the user that their BLE may be malfunctioning...
                            //  ...either reset Bluetooth, WiFi (yes, "WiFi"), reboot...
                            // ...or get a different device.
                            log(Log.WARN, bluetoothDeviceAddress, "parse", "DATA Unexpected macAddress is zero; ignoring")
                            return null
                        }
                        if (bluetoothDeviceAddress != macAddress) {
                            // TODO:(pv) Report to the user that their BLE may be malfunctioning...
                            //  ...either reset Bluetooth, WiFi (yes, "WiFi"), reboot...
                            // ...or get a different device.
                            //@formatter:off
                            log(Log.WARN, bluetoothDeviceAddress, "parse", "DATA Unexpected bluetoothDeviceAddress(${Utils.quote(bluetoothDeviceAddress)}) != macAddress(${Utils.quote(BluetoothUtils.macAddressStringToString(macAddress))}); ignoring")
                            //@formatter:on
                            return null
                        }
                        val macAddressExtraByte = manufacturerSpecificDataByteBuffer.get()
                        if (LOG_DATA_VERBOSE) {
                            //@formatter:off
                            log(Log.INFO, bluetoothDeviceAddress, "parse", "DATA macAddressExtraByte=$macAddressExtraByte (0x${Utils.toHexString(macAddressExtraByte, 1)})")
                            //@formatter:on
                        }
                        val triggers: MutableSet<BleToolParser.Trigger<*>> = LinkedHashSet<BleToolParser.Trigger<*>>()
                        val bytesRemaining = manufacturerSpecificDataByteBuffer.remaining()
                        if (false && LOG_DATA_VERBOSE) {
                            log(Log.ERROR, bluetoothDeviceAddress, "parse", "DATA bytesRemaining=$bytesRemaining")
                        }
                        if (bytesRemaining == 0) {
                            return triggers
                        }
                        val actionSequenceAndData = manufacturerSpecificDataByteBuffer.get()
                        if (LOG_DATA_VERBOSE) {
                            //@formatter:off
                            log(Log.INFO, bluetoothDeviceAddress, "parse", "DATA actionSequenceAndData=$actionSequenceAndData (0x${Utils.toHexString(actionSequenceAndData, 1)}) (0b${Utils.toBitString(actionSequenceAndData, 8)})")
                            //@formatter:on
                        }
                        val actionSequence = (actionSequenceAndData.toInt() and 240 shr 4).toByte()
                        val actionData = (actionSequenceAndData.toInt() and 15).toByte()
                        val actionDataBeepingAndFlashing = (actionData.toInt() and 8 shr 3).toByte()
                        val actionDataAdvertisementSpeed = (actionData.toInt() and 4 shr 2).toByte()
                        val actionDataButton = (actionData.toInt() and 3).toByte()
                        if (LOG_DATA_VERBOSE) {
                            //@formatter:off
                            log(Log.ERROR, bluetoothDeviceAddress, "parse", "DATA actionSequence=${BleToolParser.ActionSequence.toString(actionSequence)} (0x${Utils.toHexString(actionSequence, 1)}) (0b${Utils.toBitString(actionSequence, 4)})")
                            log(Log.ERROR, bluetoothDeviceAddress, "parse", "DATA actionData=$actionData (0x${Utils.toHexString(actionData, 1)}) (0b${Utils.toBitString(actionData, 4)})")
                            log(Log.ERROR, bluetoothDeviceAddress, "parse", "DATA actionDataBeepingAndFlashing=$actionDataBeepingAndFlashing (0x${Utils.toHexString(actionDataBeepingAndFlashing, 1)})")
                            //@formatter:on
                            when (actionDataBeepingAndFlashing) {
                                0.toByte() -> log(Log.ERROR, bluetoothDeviceAddress, "parse", "DATA actionDataBeepingAndFlashing: No")
                                1.toByte() -> log(Log.ERROR, bluetoothDeviceAddress, "parse", "DATA actionDataBeepingAndFlashing: Yes")
                                else -> log(Log.ERROR, bluetoothDeviceAddress, "parse", "DATA actionDataBeepingAndFlashing: Unknown")
                            }
                            //@formatter:off
                            log(Log.ERROR, bluetoothDeviceAddress, "parse", "DATA actionDataAdvertisementSpeed=$actionDataAdvertisementSpeed (0x${Utils.toHexString(actionDataAdvertisementSpeed, 1)})")
                            //@formatter:on
                            when (actionDataAdvertisementSpeed) {
                                //@formatter:off
                                BleToolParser.AdvertisementSpeed.FAST -> log(Log.ERROR, bluetoothDeviceAddress, "parse", "DATA actionDataAdvertisementSpeed: FAST")
                                BleToolParser.AdvertisementSpeed.SLOW -> log(Log.ERROR, bluetoothDeviceAddress, "parse", "DATA actionDataAdvertisementSpeed: SLOW")
                                else -> log(Log.ERROR, bluetoothDeviceAddress, "parse", "DATA actionDataAdvertisementSpeed: Unknown")
                                //@formatter:on
                            }
                            //@formatter:off
                            log(Log.ERROR, bluetoothDeviceAddress, "parse", "DATA actionDataButton=$actionDataButton (0x${Utils.toHexString(actionDataButton, 1)})")
                            //@formatter:on
                            when (actionDataButton) {
                                //@formatter:off
                                BleToolParser.Actions.NONE -> log(Log.ERROR, bluetoothDeviceAddress, "parse", "DATA actionDataButton: NONE")
                                BleToolParser.Actions.CLICK_SHORT -> log(Log.ERROR, bluetoothDeviceAddress, "parse", "DATA actionDataButton: CLICK_SHORT")
                                BleToolParser.Actions.CLICK_LONG -> log(Log.ERROR, bluetoothDeviceAddress, "parse", "DATA actionDataButton: CLICK_LONG")
                                BleToolParser.Actions.CLICK_DOUBLE -> log(Log.ERROR, bluetoothDeviceAddress, "parse", "DATA actionDataButton: CLICK_DOUBLE")
                                else -> log(Log.ERROR, bluetoothDeviceAddress, "parse", "DATA actionDataButton: Unknown")
                                //@formatter:on
                            }
                        }
                        val claimed = manufacturerSpecificDataByteBuffer.get()
                        if (LOG_DATA_VERBOSE) {
                            log(Log.INFO, bluetoothDeviceAddress, "parse", "DATA claimed=$claimed (0x${Utils.toHexString(claimed, 1)})")
                        }

                        val actionCounter = manufacturerSpecificDataByteBuffer.get()
                        if (LOG_DATA_VERBOSE) {
                            log(Log.INFO, bluetoothDeviceAddress, "parse", "DATA actionCounter=$actionCounter (0x${Utils.toHexString(actionCounter, 1)})")
                        }
                        val temperatureCelsius = manufacturerSpecificDataByteBuffer.short
                        if (LOG_DATA_VERBOSE) {
                            log(Log.INFO, bluetoothDeviceAddress, "parse", "DATA temperatureCelsius=$temperatureCelsius (0x${Utils.toHexString(temperatureCelsius, 2)})")
                        }
                        val batteryMilliVolts = manufacturerSpecificDataByteBuffer.short
                        if (LOG_DATA_VERBOSE) {
                            log(Log.INFO, bluetoothDeviceAddress, "parse", "DATA batteryMilliVolts=$batteryMilliVolts (0x${Utils.toHexString(batteryMilliVolts, 2)})")
                        }
                        val beaconPeriodCount = manufacturerSpecificDataByteBuffer.get()
                        if (LOG_DATA_VERBOSE) {
                            log(Log.INFO, bluetoothDeviceAddress, "parse", "DATA beaconPeriodCount=$beaconPeriodCount (0x${Utils.toHexString(beaconPeriodCount, 1)})")
                        }
                        val modelNumber = manufacturerSpecificDataByteBuffer.get()
                        if (LOG_DATA_VERBOSE) {
                            log(Log.INFO, bluetoothDeviceAddress, "parse", "DATA modelNumber=$modelNumber (0x${Utils.toHexString(modelNumber, 1)})")
                        }
                        triggers
                    } finally {
                        manufacturerSpecificDataByteBuffer.order(originalByteOrder)
                        if (LOG_DATA_VERBOSE) {
                            log(Log.INFO, bluetoothDeviceAddress, "parse", "DATA -- END ----------")
                        }
                    }
                }
                BleToolParser.BluetoothSigManufacturerIds.APPLE -> {
                    if (LOG_IBEACON_VERBOSE) {
                        log(Log.INFO, bluetoothDeviceAddress, "parse", "IBEACON -- BEGIN --------")
                    }
                    val beaconType = manufacturerSpecificDataByteBuffer.get()
                    if (LOG_IBEACON_VERBOSE) {
                        log(Log.INFO, bluetoothDeviceAddress, "parse", "IBEACON beaconType=$beaconType (0x${Utils.toHexString(beaconType, 1)})")
                    }
                    val beaconLength = manufacturerSpecificDataByteBuffer.get()
                    if (LOG_IBEACON_VERBOSE) {
                        log(Log.INFO, bluetoothDeviceAddress, "parse", "IBEACON beaconLength=$beaconLength (0x${Utils.toHexString(beaconLength, 1)})")
                    }
                    val uuidMostSignificantBits = manufacturerSpecificDataByteBuffer.long
                    val uuidLeastSignificantBits = manufacturerSpecificDataByteBuffer.long
                    val uuid = UUID(uuidMostSignificantBits, uuidLeastSignificantBits).toString()
                    if (LOG_IBEACON_VERBOSE) {
                        log(Log.INFO, bluetoothDeviceAddress, "parse", "IBEACON uuid=$uuid")
                    }
                    val major = manufacturerSpecificDataByteBuffer.short
                    if (LOG_IBEACON_VERBOSE) {
                        log(Log.INFO, bluetoothDeviceAddress, "parse", "IBEACON major=0x${Utils.toHexString(major, 2)}")
                    }
                    val minor = manufacturerSpecificDataByteBuffer.short
                    if (LOG_IBEACON_VERBOSE) {
                        log(Log.INFO, bluetoothDeviceAddress, "parse", "IBEACON minor=0x${Utils.toHexString(minor, 2)}")
                    }
                    val power = manufacturerSpecificDataByteBuffer.get()
                    if (LOG_IBEACON_VERBOSE) {
                        log(Log.INFO, bluetoothDeviceAddress, "parse", "IBEACON power=$power (0x${Utils.toHexString(power, 1)})")
                    }
                    if (LOG_IBEACON_VERBOSE) {
                        log(Log.INFO, bluetoothDeviceAddress, "parse", "IBEACON -- END ----------")
                    }
                    when (uuid) {
                        BleToolParser.Regions.TRACKING_FINDER, BleToolParser.Regions.TRACKING_STONE -> {
                            if (LOG_REGION) {
                                log(Log.INFO, bluetoothDeviceAddress, "parse", "IBEACON REGION TRACKING")
                            }
                            val triggers: MutableSet<BleToolParser.Trigger<*>> = LinkedHashSet<BleToolParser.Trigger<*>>()
                            return triggers
                        }
                        BleToolParser.Regions.INTERRUPT -> {
                            if (LOG_REGION) {
                                log(Log.INFO, bluetoothDeviceAddress, "parse", "IBEACON REGION INTERRUPT")
                            }
                            val triggers: MutableSet<BleToolParser.Trigger<*>> = LinkedHashSet<BleToolParser.Trigger<*>>()
                            return triggers
                        }
                        else -> {
                            if (LOG_REGION) {
                                //@formatter:off
                                log(Log.VERBOSE, bluetoothDeviceAddress, "parse", "IBEACON REGION Unknown $debugModelName uuid=${Utils.quote(uuid)}; ignoring")
                                //@formatter:on
                            }
                        }
                    }
                }
            }

            return null
        }
    }
}