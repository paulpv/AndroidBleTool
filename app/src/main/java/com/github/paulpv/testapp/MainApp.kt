package com.github.paulpv.testapp

import android.app.Activity
import android.app.Application
import android.bluetooth.le.ScanFilter
import com.github.paulpv.androidbletool.*
import com.github.paulpv.androidbletool.BleTool.BleToolConfiguration

class MainApp : Application(), BleTool.BleToolApplication {

    private val scanningNotificationInfo = object : BleTool.BleToolScanningNotificationInfo {
        override val activityClass: Class<out Activity>
            get() = MainActivity::class.java
        override val channelDescription: String
            get() = getString(R.string.notification_scanning_channel_description)

        override fun getSmallIcon(isForegrounded: Boolean): Int {
            return if (isForegrounded) R.drawable.ic_launcher_foreground else R.drawable.ic_launcher_background
        }

        override val contentTitle: String
            get() = getString(R.string.app_name)


        override fun getContentText(isBluetoothEnabled: Boolean): String {
            val resId = if (isBluetoothEnabled) R.string.scanning else R.string.waiting_for_bluetooth
            return getString(resId)
        }
    }

    private val bleToolConfiguration = object : BleToolConfiguration() {
        override val scanningNotificationInfo: BleTool.BleToolScanningNotificationInfo
            get() = this@MainApp.scanningNotificationInfo
        override val SCAN_FILTERS: List<ScanFilter>
            get() = BuildConfig.SCAN_FILTERS
        override val DEBUG_DEVICE_ADDRESS_FILTER: Set<String>?
            get() = BuildConfig.DEBUG_DEVICE_ADDRESS_FILTER
        override val SCAN_PARSERS: List<BleToolParser.BleDeviceParser>
            get() = BuildConfig.SCAN_PARSERS
        override val DEVICE_FACTORY: BleDeviceFactory<*>
            get() = BuildConfig.DEVICE_FACTORY
    }

    private lateinit var _bleTool: BleTool

    override val bleTool: BleTool
        get() = _bleTool

    override fun onCreate() {
        super.onCreate()
        _bleTool = BleTool(this, bleToolConfiguration)
    }
}
