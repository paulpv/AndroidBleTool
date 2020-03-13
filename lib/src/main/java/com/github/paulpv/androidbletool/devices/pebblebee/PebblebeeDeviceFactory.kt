package com.github.paulpv.androidbletool.devices.pebblebee

import android.bluetooth.BluetoothDevice
import com.github.paulpv.androidbletool.BleDeviceFactory
import com.github.paulpv.androidbletool.BleToolParser
import com.github.paulpv.androidbletool.BluetoothUtils
import com.github.paulpv.androidbletool.devices.Triggers

class PebblebeeDeviceFactory : BleDeviceFactory<PebblebeeDevice>() {
    override fun getDevice(
        bluetoothDevice: BluetoothDevice,
        parser: BleToolParser.BleDeviceParser,
        triggers: Set<Triggers.Trigger<*>>
    ): PebblebeeDevice {
        var pebblebeeDeviceModelNumber = Pebblebee.DeviceModelNumber.UNKNOWN
        val itTriggers = triggers.iterator()
        while (itTriggers.hasNext()) {
            val trigger = itTriggers.next()
            if (trigger is Triggers.TriggerModelNumber) {
                pebblebeeDeviceModelNumber = trigger.value
                break
            }
        }
        if (pebblebeeDeviceModelNumber == Pebblebee.DeviceModelNumber.UNKNOWN) {
            pebblebeeDeviceModelNumber = parser.modelNumber
        }
        return getDevice(bluetoothDevice.address, pebblebeeDeviceModelNumber)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun getDevice(macAddress: String, pebblebeeDeviceModelNumber: Int): PebblebeeDevice {
        return getDevice(BluetoothUtils.macAddressStringToLong(macAddress), pebblebeeDeviceModelNumber)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun getDevice(macAddress: Long, pebblebeeDeviceModelNumber: Int): PebblebeeDevice {
        synchronized(deviceCache) {
            var device = deviceCache[macAddress]
            if (device != null) return device
            val gattHandler = gattManager.getGattHandler(macAddress)
            device = when (pebblebeeDeviceModelNumber) {
                Pebblebee.DeviceModelNumber.FINDER2_0 -> {
                    PebblebeeDeviceFinder2(gattHandler)
                }
                else -> {
                    null
                }
            }
            if (device != null) {
                deviceCache.put(macAddress, device)
            }
            return device
        }
    }
}