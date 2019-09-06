package com.github.paulpv.androidbletool

import android.app.PendingIntent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.polidea.rxandroidble2.exceptions.BleScanException
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanSettings
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val rxBleClient = MainApplication.rxBleClient

    private lateinit var callbackIntent: PendingIntent

    private var hasClickedScan = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        callbackIntent = BleScanReceiver.newPendingIntent(this)

        scan_start_btn.setOnClickListener { onScanStartClick() }
        scan_stop_btn.setOnClickListener { onScanStopClick() }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (isLocationPermissionGranted(requestCode, grantResults) && hasClickedScan) {
            hasClickedScan = false
            scanBleDeviceInBackground()
        }
    }

    private fun onScanStartClick() {
        if (isLocationPermissionGranted()) {
            scanBleDeviceInBackground()
        } else {
            hasClickedScan = true
            requestLocationPermission()
        }
    }

    private fun onScanStopClick() {
        rxBleClient.backgroundScanner.stopBackgroundBleScan(callbackIntent)
    }

    private fun scanBleDeviceInBackground() {
        try {
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()

            val scanFilter = ScanFilter.Builder()
                // add custom filters if needed
                .build()

            rxBleClient.backgroundScanner.scanBleDeviceInBackground(
                callbackIntent,
                scanSettings,
                scanFilter
            )
        } catch (scanException: BleScanException) {
            Log.e("BackgroundScanActivity", "Failed to start background scan", scanException)
            showError(scanException)
        }
    }
}
