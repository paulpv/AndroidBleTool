package com.github.paulpv.testapp

import android.app.Activity
import android.app.Application
import android.bluetooth.le.ScanFilter
import com.github.paulpv.androidbletool.BleTool
import com.github.paulpv.androidbletool.BleTool.BleToolConfiguration
import com.github.paulpv.androidbletool.R

class MainApp : Application(), BleTool.BleToolApplication {

    private lateinit var bleToolConfiguration: BleToolConfiguration

    override val bleTool: BleTool
        get() = _bleTool
    private lateinit var _bleTool: BleTool

    val scanningNotificationInfo = object : BleTool.BleToolScanningNotificationInfo {
        override val activityClass: Class<out Activity>
            get() = MainActivity::class.java
        override val channelDescription: String
            get() = getString(R.string.notification_scanning_channel_description)
        override val contentTitle: String
            get() = getString(R.string.app_name)

        override fun getSmallIcon(isForegrounded: Boolean): Int {
            return if (isForegrounded) R.drawable.ic_launcher_foreground else R.drawable.ic_launcher_background
        }

        override fun getText(isBluetoothEnabled: Boolean): String {
            val resId = if (isBluetoothEnabled) R.string.scanning else R.string.waiting_for_bluetooth
            return getString(resId)
        }
    }

    override fun onCreate() {
        super.onCreate()

        bleToolConfiguration = object : BleToolConfiguration {
            override val scanningNotificationInfo: BleTool.BleToolScanningNotificationInfo
                get() = this@MainApp.scanningNotificationInfo

            override fun addScanFilters(scanFilters: MutableList<ScanFilter>) {
                scanFilters.add(
                    ScanFilter.Builder()
                        .setDeviceName("FNDR")
                        //.setDeviceAddress("0E:06:E5:75:F0:AE")
                        .build()
                )
                /*
                scanFilters.add(
                    ScanFilter.Builder()
                        .setDeviceName("FNDR")
                        .setDeviceAddress("0E:06:E5:E2:73:AF")
                        .build()
                )
                */
                /*
                scanFilters.add(
                  ScanFilter.Builder()
                    .setDeviceName("FNDR")
                    .setDeviceAddress("0E:06:E5:E6:E7:AE")
                    .build()
                )
                */
            }
        }

        _bleTool = BleTool(this, bleToolConfiguration)
    }
}
