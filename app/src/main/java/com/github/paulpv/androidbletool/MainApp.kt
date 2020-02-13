package com.github.paulpv.androidbletool

import android.app.Activity
import android.app.Application
import android.bluetooth.le.ScanFilter
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
                        .build()
                )
            }
        }

        _bleTool = BleTool(this, bleToolConfiguration)
    }
}
