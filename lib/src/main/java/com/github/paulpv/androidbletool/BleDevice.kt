package com.github.paulpv.androidbletool

import com.github.paulpv.androidbletool.gatt.GattHandler
import com.github.paulpv.androidbletool.utils.ReflectionUtils

open class BleDevice(val gattHandler: GattHandler) {
    companion object {
        fun toString(device: BleDevice?, suffix: String? = null): String {
            if (device == null) {
                return "null"
            }
            val sb = StringBuilder()
            sb.append(ReflectionUtils.instanceName(device))
            sb.append("{")
                .append(" macAddressString=").append(device.macAddressString)
                .append(", macAddressLong=").append(device.macAddressLong)
            if (suffix != null) {
                sb.append(suffix)
            }
            sb.append(" }")
            return sb.toString()
        }
    }

    interface RequestProgress {
        fun onConnecting()
        fun onConnected()
        fun onRequesting()
        fun onRequested(success: Boolean)
        fun onDisconnecting()
        fun onDisconnected(success: Boolean)
    }

    val macAddressString = gattHandler.deviceAddressString
    protected val macAddressLong = gattHandler.deviceAddressLong

    override fun toString(): String {
        return toString(this)
    }
}