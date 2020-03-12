package com.github.paulpv.androidbletool.devices

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import android.os.ParcelUuid
import android.util.Log
import androidx.core.util.Consumer
import com.github.paulpv.androidbletool.BleDevice
import com.github.paulpv.androidbletool.BleToolParser
import com.github.paulpv.androidbletool.BleToolParser.BluetoothSigManufacturerIds
import com.github.paulpv.androidbletool.BleToolParser.Configuration
import com.github.paulpv.androidbletool.BluetoothUtils
import com.github.paulpv.androidbletool.BuildConfig
import com.github.paulpv.androidbletool.devices.PebblebeeDevices.*
import com.github.paulpv.androidbletool.devices.Triggers.Trigger
import com.github.paulpv.androidbletool.devices.Triggers.TriggerAdvertisementSpeed.AdvertisementSpeed
import com.github.paulpv.androidbletool.gatt.GattHandler
import com.github.paulpv.androidbletool.gatt.GattUuids
import com.github.paulpv.androidbletool.utils.Utils
import com.github.paulpv.androidbletool.utils.Utils.TAG
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

object PebblebeeDeviceFinder2 {
    private val TAG = TAG(PebblebeeDeviceFinder2::class.java)

    @Suppress("unused")
    private val PLAY_JINGLE_COUNT_1 = byteArrayOf(0x01, 0x00)

    @Suppress("unused")
    private val PLAY_JINGLE_COUNT_2 = byteArrayOf(0x01, 0x00, 0x00)

    @Suppress("unused")
    private val PLAY_JINGLE_COUNT_3 = byteArrayOf(0x01, 0x00, 0x00, 0x00)

    @Suppress("unused")
    private val PLAY_JINGLE_COUNT_4 = byteArrayOf(0x80.toByte(), 0x01)

    @JvmStatic
    fun requestBeep(bleDevice: BleDevice, callbacks: PebblebeeDevice.RequestProgress? = null) {
        val gattHandler = bleDevice.gattHandler

        val runDisconnect = Consumer<Boolean> { success ->
            Log.i(TAG, "DISCONNECTING")
            callbacks?.onDisconnecting()
            if (!gattHandler.disconnect(runAfterDisconnect = Runnable {
                    Log.i(TAG, "DISCONNECTED!")
                    callbacks?.onDisconnected(success)
                })) {
                Log.e(TAG, "disconnect failed")
                callbacks?.onDisconnected(false)
            }
        }

        val runBeep = Runnable {
            val service = GattUuids.PEBBLEBEE_FINDER_SERVICE.uuid
            val characteristic = GattUuids.PEBBLEBEE_FINDER_CHARACTERISTIC1.uuid
            val value = PLAY_JINGLE_COUNT_4
            Log.i(TAG, "REQUESTING")
            callbacks?.onRequesting()
            if (!gattHandler.characteristicWrite(
                    serviceUuid = service,
                    characteristicUuid = characteristic,
                    value = value,
                    characteristicWriteType = GattHandler.CharacteristicWriteType.DefaultWithResponse,
                    runAfterSuccess = Runnable {
                        Log.i(TAG, "REQUEST SUCCESS!")
                        callbacks?.onRequested(true)
                        runDisconnect.accept(true)
                    },
                    runAfterFail = Runnable {
                        Log.e(TAG, "REQUEST FAIL!")
                        callbacks?.onRequested(false)
                        runDisconnect.accept(false)
                    }
                )
            ) {
                Log.e(TAG, "characteristicWrite failed")
                runDisconnect.accept(false)
            }
        }

        if (gattHandler.isConnectingOrConnectedAndNotDisconnecting) {
            runBeep.run()
        } else {
            Log.i(TAG, "CONNECTING")
            callbacks?.onConnecting()
            if (!gattHandler.connect(runAfterConnect = Runnable {
                    Log.i(TAG, "CONNECT SUCCESS!")
                    callbacks?.onConnected()
                    runBeep.run()
                }, runAfterFail = Runnable {
                    Log.e(TAG, "CONNECT FAIL!")
                    runDisconnect.accept(false)
                })) {
                Log.e(TAG, "connect failed")
                runDisconnect.accept(false)
            }
        }
    }

    class Parser : BleToolParser.AbstractParser(
        TAG, "Finder2", Configuration()
            .addDeviceAddressPrefixFilter(PebblebeeMacAddressPrefix.PEBBLEBEE_FINDER2)
            .addDeviceName(PebblebeeDeviceCaseSensitiveName.FINDER)
            .addServiceUuid(GattUuids.PEBBLEBEE_FINDER_SERVICE)
    ) {

        @Suppress("SimplifyBooleanWithConstants", "PrivatePropertyName")
        private val LOG_IGNORED_MAC_ADDRESS = false && BuildConfig.DEBUG

        @Suppress("SimplifyBooleanWithConstants", "PrivatePropertyName")
        private val LOG_IBEACON_VERBOSE = false && BuildConfig.DEBUG

        @Suppress("SimplifyBooleanWithConstants", "PrivatePropertyName")
        private val LOG_REGION = false && BuildConfig.DEBUG

        @Suppress("SimplifyBooleanWithConstants", "PrivatePropertyName")
        private val LOG_DATA = true && BuildConfig.DEBUG

        @Suppress("SimplifyBooleanWithConstants", "PrivatePropertyName")
        private val LOG_DATA_VERBOSE = true && BuildConfig.DEBUG

        override fun parseScan(
            scanRecord: ScanRecord,
            bluetoothDevice: BluetoothDevice,
            serviceUuids: MutableList<ParcelUuid>,
            manufacturerId: Int,
            manufacturerSpecificDataByteBuffer: ByteBuffer,
            triggers: MutableSet<Trigger<*>>
        ): Boolean {

            val methodName = "parseScan"

            val bluetoothDeviceAddress = bluetoothDevice.address

            if (!isSupportedDeviceAddressPrefix(bluetoothDevice)) {
                if (LOG_IGNORED_MAC_ADDRESS) {
                    val bluetoothDeviceAddressPrefix = BleToolParser.getMacAddressPrefix(bluetoothDeviceAddress)
                    //@formatter:off
                    log(Log.VERBOSE, bluetoothDeviceAddress, methodName, " Non-$debugModelName macAddress; bluetoothDeviceAddress=${Utils.quote(bluetoothDeviceAddress)}; bluetoothDeviceAddressPrefix=${Utils.quote(bluetoothDeviceAddressPrefix)}; ignoring")
                    //@formatter:on
                }
                return false
            }

            when (manufacturerId) {
                PebblebeeManufacturerIds.PEBBLEBEE_FINDER2 -> {
                    val bluetoothDeviceName = getDeviceNameOrScanRecordName(bluetoothDevice, scanRecord)
                    if (LOG_DATA_VERBOSE) {
                        log(Log.INFO, bluetoothDeviceAddress, methodName, "bluetoothDeviceName=${Utils.quote(bluetoothDeviceName)}")
                    }
                    if (!isSupportedDeviceName(bluetoothDeviceName)) {
                        //if (!callbacks.isWhitelisted(bluetoothDeviceName, bluetoothDeviceAddress)) {
                        if (LOG_DATA_VERBOSE) {
                            //@formatter:off
                            log(Log.VERBOSE, bluetoothDeviceAddress, methodName, "Non-$debugModelName name; bluetoothDeviceAddress=${Utils.quote(bluetoothDeviceAddress)}; bluetoothDeviceName=${Utils.quote(bluetoothDeviceName)}; ignoring")
                            //@formatter:on
                        }
                        return false
                        //}
                    }
                    if (!isSupportedServices(serviceUuids)) {
                        if (LOG_DATA_VERBOSE) {
                            //@formatter:off
                            log(Log.VERBOSE, bluetoothDeviceAddress, methodName, "mServiceUuids != serviceUuids; bluetoothDeviceAddress=${Utils.quote(bluetoothDeviceAddress)}; ignoring")
                            //@formatter:on
                        }
                        return false
                    }

                    //
                    // Process as 2
                    //
                    if (LOG_DATA) {
                        log(Log.INFO, bluetoothDeviceAddress, methodName, "DATA")
                    }
                    val originalByteOrder = manufacturerSpecificDataByteBuffer.order()
                    return try {
                        if (LOG_DATA_VERBOSE) {
                            log(Log.INFO, bluetoothDeviceAddress, methodName, "DATA -- BEGIN --------")
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
                            log(Log.INFO, bluetoothDeviceAddress, methodName, "DATA macAddress=${Utils.quote(macAddress)}")
                        }
                        if (macAddress == "00:00:00:00:00:00") {
                            // TODO:(pv) Report to the user that their BLE may be malfunctioning...
                            //  ...either reset Bluetooth, WiFi (yes, "WiFi"), reboot...
                            // ...or get a different device.
                            log(Log.WARN, bluetoothDeviceAddress, methodName, "DATA Unexpected macAddress is zero; ignoring")
                            return false
                        }
                        if (bluetoothDeviceAddress != macAddress) {
                            // TODO:(pv) Report to the user that their BLE may be malfunctioning...
                            //  ...either reset Bluetooth, WiFi (yes, "WiFi"), reboot...
                            // ...or get a different device.
                            //@formatter:off
                            log(Log.WARN, bluetoothDeviceAddress, methodName, "DATA Unexpected bluetoothDeviceAddress(${Utils.quote(bluetoothDeviceAddress)}) != macAddress(${Utils.quote(BluetoothUtils.macAddressStringToString(macAddress))}); ignoring")
                            //@formatter:on
                            return false
                        }
                        val macAddressExtraByte = manufacturerSpecificDataByteBuffer.get()
                        if (LOG_DATA_VERBOSE) {
                            //@formatter:off
                            log(Log.INFO, bluetoothDeviceAddress, methodName, "DATA macAddressExtraByte=$macAddressExtraByte (0x${Utils.toHexString(macAddressExtraByte, 1)})")
                            //@formatter:on
                        }
                        val bytesRemaining = manufacturerSpecificDataByteBuffer.remaining()
                        @Suppress("SimplifyBooleanWithConstants")
                        if (false && LOG_DATA_VERBOSE) {
                            log(Log.ERROR, bluetoothDeviceAddress, methodName, "DATA bytesRemaining=$bytesRemaining")
                        }
                        if (bytesRemaining == 0) {
                            triggers.add(Triggers.TriggerAdvertisementSpeed(AdvertisementSpeed.SLOW))
                            return true
                        }
                        val actionSequenceAndData = manufacturerSpecificDataByteBuffer.get()
                        if (LOG_DATA_VERBOSE) {
                            //@formatter:off
                            log(Log.INFO, bluetoothDeviceAddress, methodName, "DATA actionSequenceAndData=$actionSequenceAndData (0x${Utils.toHexString(actionSequenceAndData, 1)}) (0b${Utils.toBitString(actionSequenceAndData, 8)})")
                            //@formatter:on
                        }
                        val actionSequence = (actionSequenceAndData.toInt() and 240 shr 4).toByte()
                        val actionData = (actionSequenceAndData.toInt() and 15).toByte()
                        val actionDataBeepingAndFlashing = (actionData.toInt() and 8 shr 3).toByte()
                        val actionDataAdvertisementSpeed = (actionData.toInt() and 4 shr 2).toByte()
                        val actionDataButton = (actionData.toInt() and 3).toByte()
                        if (LOG_DATA_VERBOSE) {
                            //@formatter:off
                            log(Log.ERROR, bluetoothDeviceAddress, methodName, "DATA actionSequence=${ActionSequence.toString(actionSequence)} (0x${Utils.toHexString(actionSequence, 1)}) (0b${Utils.toBitString(actionSequence, 4)})")
                            log(Log.ERROR, bluetoothDeviceAddress, methodName, "DATA actionData=$actionData (0x${Utils.toHexString(actionData, 1)}) (0b${Utils.toBitString(actionData, 4)})")
                            log(Log.ERROR, bluetoothDeviceAddress, methodName, "DATA actionDataBeepingAndFlashing=$actionDataBeepingAndFlashing (0x${Utils.toHexString(actionDataBeepingAndFlashing, 1)})")
                            //@formatter:on
                            when (actionDataBeepingAndFlashing) {
                                0.toByte() -> log(Log.ERROR, bluetoothDeviceAddress, methodName, "DATA actionDataBeepingAndFlashing: No")
                                1.toByte() -> log(Log.ERROR, bluetoothDeviceAddress, methodName, "DATA actionDataBeepingAndFlashing: Yes")
                                else -> log(Log.ERROR, bluetoothDeviceAddress, methodName, "DATA actionDataBeepingAndFlashing: Unknown")
                            }
                            //@formatter:off
                            log(Log.ERROR, bluetoothDeviceAddress, methodName, "DATA actionDataAdvertisementSpeed=$actionDataAdvertisementSpeed (0x${Utils.toHexString(actionDataAdvertisementSpeed, 1)})")
                            //@formatter:on
                            when (actionDataAdvertisementSpeed) {
                                //@formatter:off
                                AdvertisementSpeed.FAST -> log(Log.ERROR, bluetoothDeviceAddress, methodName, "DATA actionDataAdvertisementSpeed: FAST")
                                AdvertisementSpeed.SLOW -> log(Log.ERROR, bluetoothDeviceAddress, methodName, "DATA actionDataAdvertisementSpeed: SLOW")
                                else -> log(Log.ERROR, bluetoothDeviceAddress, methodName, "DATA actionDataAdvertisementSpeed: Unknown")
                                //@formatter:on
                            }
                            //@formatter:off
                            log(Log.ERROR, bluetoothDeviceAddress, methodName, "DATA actionDataButton=$actionDataButton (0x${Utils.toHexString(actionDataButton, 1)})")
                            //@formatter:on
                            when (actionDataButton) {
                                //@formatter:off
                                Actions.NONE -> log(Log.ERROR, bluetoothDeviceAddress, methodName, "DATA actionDataButton: NONE")
                                Actions.CLICK_SHORT -> log(Log.ERROR, bluetoothDeviceAddress, methodName, "DATA actionDataButton: CLICK_SHORT")
                                Actions.CLICK_LONG -> log(Log.ERROR, bluetoothDeviceAddress, methodName, "DATA actionDataButton: CLICK_LONG")
                                Actions.CLICK_DOUBLE -> log(Log.ERROR, bluetoothDeviceAddress, methodName, "DATA actionDataButton: CLICK_DOUBLE")
                                else -> log(Log.ERROR, bluetoothDeviceAddress, methodName, "DATA actionDataButton: Unknown")
                                //@formatter:on
                            }
                            /*
                            //@formatter:off
                            log(Log.ERROR, bluetoothDeviceAddress, methodName, "#FAST callbacks.isInForeground() == ${callbacks.isInForeground()}")
                            //@formatter:on
                            */
                        }
                        val claimed = manufacturerSpecificDataByteBuffer.get()
                        if (LOG_DATA_VERBOSE) {
                            log(Log.INFO, bluetoothDeviceAddress, methodName, "DATA claimed=$claimed (0x${Utils.toHexString(claimed, 1)})")
                        }

                        val actionCounter = manufacturerSpecificDataByteBuffer.get()
                        if (LOG_DATA_VERBOSE) {
                            log(Log.INFO, bluetoothDeviceAddress, methodName, "DATA actionCounter=$actionCounter (0x${Utils.toHexString(actionCounter, 1)})")
                        }
                        val temperatureCelsius = manufacturerSpecificDataByteBuffer.short
                        if (LOG_DATA_VERBOSE) {
                            log(Log.INFO, bluetoothDeviceAddress, methodName, "DATA temperatureCelsius=$temperatureCelsius (0x${Utils.toHexString(temperatureCelsius, 2)})")
                        }
                        val batteryMilliVolts = manufacturerSpecificDataByteBuffer.short
                        if (LOG_DATA_VERBOSE) {
                            log(Log.INFO, bluetoothDeviceAddress, methodName, "DATA batteryMilliVolts=$batteryMilliVolts (0x${Utils.toHexString(batteryMilliVolts, 2)})")
                        }
                        val beaconPeriodCount = manufacturerSpecificDataByteBuffer.get()
                        if (LOG_DATA_VERBOSE) {
                            log(Log.INFO, bluetoothDeviceAddress, methodName, "DATA beaconPeriodCount=$beaconPeriodCount (0x${Utils.toHexString(beaconPeriodCount, 1)})")
                        }
                        val modelNumber = manufacturerSpecificDataByteBuffer.get()
                        if (LOG_DATA_VERBOSE) {
                            log(Log.INFO, bluetoothDeviceAddress, methodName, "DATA modelNumber=$modelNumber (0x${Utils.toHexString(modelNumber, 1)})")
                        }
                        triggers.add(Triggers.TriggerBeepingAndFlashing(actionDataBeepingAndFlashing.toInt() == 1))
                        triggers.add(Triggers.TriggerShortClick(actionDataButton == Actions.CLICK_SHORT, actionSequence, actionCounter))
                        triggers.add(Triggers.TriggerLongClick(actionDataButton == Actions.CLICK_LONG, actionSequence, actionCounter))
                        triggers.add(Triggers.TriggerDoubleClick(actionDataButton == Actions.CLICK_DOUBLE, actionSequence, actionCounter))
                        triggers.add(Triggers.TriggerTemperatureCelsius(temperatureCelsius))
                        triggers.add(Triggers.TriggerBatteryLevelMilliVolts(batteryMilliVolts))
                        triggers.add(Triggers.TriggerAdvertisementSpeed(actionDataAdvertisementSpeed))
                        true
                    } finally {
                        manufacturerSpecificDataByteBuffer.order(originalByteOrder)
                        if (LOG_DATA_VERBOSE) {
                            log(Log.INFO, bluetoothDeviceAddress, methodName, "DATA -- END ----------")
                        }
                    }
                }
                BluetoothSigManufacturerIds.APPLE -> {
                    if (LOG_IBEACON_VERBOSE) {
                        log(Log.INFO, bluetoothDeviceAddress, methodName, "IBEACON -- BEGIN --------")
                    }
                    val beaconType = manufacturerSpecificDataByteBuffer.get()
                    if (LOG_IBEACON_VERBOSE) {
                        log(Log.INFO, bluetoothDeviceAddress, methodName, "IBEACON beaconType=$beaconType (0x${Utils.toHexString(beaconType, 1)})")
                    }
                    val beaconLength = manufacturerSpecificDataByteBuffer.get()
                    if (LOG_IBEACON_VERBOSE) {
                        log(Log.INFO, bluetoothDeviceAddress, methodName, "IBEACON beaconLength=$beaconLength (0x${Utils.toHexString(beaconLength, 1)})")
                    }
                    val uuidMostSignificantBits = manufacturerSpecificDataByteBuffer.long
                    val uuidLeastSignificantBits = manufacturerSpecificDataByteBuffer.long
                    val uuid = UUID(uuidMostSignificantBits, uuidLeastSignificantBits).toString()
                    if (LOG_IBEACON_VERBOSE) {
                        log(Log.INFO, bluetoothDeviceAddress, methodName, "IBEACON uuid=$uuid")
                    }
                    val major = manufacturerSpecificDataByteBuffer.short
                    if (LOG_IBEACON_VERBOSE) {
                        log(Log.INFO, bluetoothDeviceAddress, methodName, "IBEACON major=0x${Utils.toHexString(major, 2)}")
                    }
                    val minor = manufacturerSpecificDataByteBuffer.short
                    if (LOG_IBEACON_VERBOSE) {
                        log(Log.INFO, bluetoothDeviceAddress, methodName, "IBEACON minor=0x${Utils.toHexString(minor, 2)}")
                    }
                    val power = manufacturerSpecificDataByteBuffer.get()
                    if (LOG_IBEACON_VERBOSE) {
                        log(Log.INFO, bluetoothDeviceAddress, methodName, "IBEACON power=$power (0x${Utils.toHexString(power, 1)})")
                    }
                    if (LOG_IBEACON_VERBOSE) {
                        log(Log.INFO, bluetoothDeviceAddress, methodName, "IBEACON -- END ----------")
                    }
                    when (uuid) {
                        Regions.TRACKING_FINDER, Regions.TRACKING_STONE -> {
                            if (LOG_REGION) {
                                log(Log.INFO, bluetoothDeviceAddress, methodName, "IBEACON REGION TRACKING")
                            }
                            triggers.add(Triggers.TriggerShortClick(false))
                            triggers.add(Triggers.TriggerLongClick(false))
                            triggers.add(Triggers.TriggerMotion(false))
                            return true
                        }
                        Regions.INTERRUPT -> {
                            if (LOG_REGION) {
                                log(Log.INFO, bluetoothDeviceAddress, methodName, "IBEACON REGION INTERRUPT")
                            }
                            triggers.add(Triggers.TriggerContinuousScan())
                            triggers.add(Triggers.TriggerShortClick(false))
                            triggers.add(Triggers.TriggerLongClick(false))
                            return true
                        }
                        /*
                        case Regions.MOTION: {
                            if (LOG_REGION) {
                                log(PbLogLevel.Info, bluetoothDeviceAddress, methodName, "IBEACON REGION MOTION");
                            }
                            // non-null to allow processing of this device and reset click state
                            triggers.add(new TriggerMotion(true));
                            triggers.add(new TriggerShortClick(false));
                            triggers.add(new TriggerLongClick(false));
                            return true;
                        }
                        */
                        else -> {
                            if (LOG_REGION) {
                                //@formatter:off
                                log(Log.VERBOSE, bluetoothDeviceAddress, methodName, "IBEACON REGION Unknown $debugModelName uuid=${Utils.quote(uuid)}; ignoring")
                                //@formatter:on
                            }
                        }
                    }
                }
            }

            return false
        }
    }
}