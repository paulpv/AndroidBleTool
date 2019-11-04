package com.github.paulpv.androidbletool

import android.app.Activity
import android.app.Application
import android.os.Looper
import com.github.paulpv.androidbletool.BleTool.BleToolConfiguration
import com.polidea.rxandroidble2.scan.ScanFilter

class MainApp : Application(), BleTool.BleToolApplication {

    private lateinit var bleToolConfiguration: BleToolConfiguration

    override val bleTool: BleTool
        get() = _bleTool
    private lateinit var _bleTool: BleTool

    override fun onCreate() {
        super.onCreate()

        bleToolConfiguration = object : BleToolConfiguration {
            override val application: Application
                get() = this@MainApp
            override val looper: Looper?
                get() = null
            override val scanningNotificationActivityClass: Class<out Activity>
                get() = MainActivity::class.java

            override fun getScanFilters(scanFilters: MutableList<ScanFilter>) {
            }
        }

        _bleTool = BleTool(bleToolConfiguration)
    }
}
