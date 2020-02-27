package com.github.paulpv.testapp

import android.app.Activity
import android.app.Application
import android.bluetooth.le.ScanFilter
import com.github.paulpv.androidbletool.BleTool
import com.github.paulpv.androidbletool.BleTool.BleToolConfiguration

class MainApp : Application(), BleTool.BleToolApplication {

    private lateinit var bleToolConfiguration: BleToolConfiguration

    override val bleTool: BleTool
        get() = _bleTool
    private lateinit var _bleTool: BleTool

    override fun onCreate() {
        super.onCreate()

        bleToolConfiguration = object : BleToolConfiguration {
            override val scanningNotificationActivityClass: Class<out Activity>
                get() = MainActivity::class.java

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
