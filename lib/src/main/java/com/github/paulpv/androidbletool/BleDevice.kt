package com.github.paulpv.androidbletool

import com.github.paulpv.androidbletool.gatt.GattHandler
import com.github.paulpv.androidbletool.gatt.GattManager

class BleDevice private constructor(val gattHandler: GattHandler) {
    companion object {
        internal fun newDevice(gattManager: GattManager, macAddress: String): BleDevice {
            val macAddressLong = BluetoothUtils.macAddressStringToLong(macAddress)
            val gattHandler = gattManager.getGattHandler(macAddressLong)
            return BleDevice(gattHandler)
        }
    }
}