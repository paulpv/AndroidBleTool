package com.github.paulpv.androidbletool

import android.bluetooth.BluetoothDevice
import com.github.paulpv.androidbletool.BleToolParser.BleDeviceParser
import com.github.paulpv.androidbletool.collections.IterableLongSparseArray
import com.github.paulpv.androidbletool.devices.Triggers
import com.github.paulpv.androidbletool.gatt.GattManager

open class BleDeviceFactory<T : BleDevice> {
    protected val deviceCache = IterableLongSparseArray<T>()

    protected lateinit var gattManager: GattManager
        private set

    fun initialize(gattManager: GattManager) {
        this.gattManager = gattManager
    }

    fun close() {
        val it = deviceCache.iterateValues()
        while (it.hasNext()) {
            it.next().gattHandler.disconnect()
            it.remove()
        }
        gattManager.close()
    }

    fun getDevice(bluetoothDevice: BluetoothDevice): T {
        return getDevice(bluetoothDevice.address)
    }

    open fun getDevice(bluetoothDevice: BluetoothDevice, parser: BleDeviceParser, triggers: Set<Triggers.Trigger<*>>): T {
        return getDevice(bluetoothDevice.address)
    }

    fun getDevice(macAddress: String): T {
        return getDevice(BluetoothUtils.macAddressStringToLong(macAddress))
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun getDevice(macAddress: Long): T {
        synchronized(deviceCache) {
            var device = deviceCache[macAddress]
            if (device != null) return device
            val gattHandler = gattManager.getGattHandler(macAddress)
            @Suppress("UNCHECKED_CAST")
            device = BleDevice(gattHandler) as T
            deviceCache.put(macAddress, device)
            return device
        }
    }
}