package com.github.paulpv.androidbletool

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.polidea.rxandroidble2.exceptions.BleScanException

class BleScanReceiver : BroadcastReceiver() {

    companion object {

        private const val SCAN_REQUEST_CODE = 69

        fun newPendingIntent(context: Context): PendingIntent =
            Intent(context, BleScanReceiver::class.java).let {
                PendingIntent.getBroadcast(context, SCAN_REQUEST_CODE, it, 0)
            }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val backgroundScanner = MainApplication.rxBleClient.backgroundScanner
        try {
            val scanResults = backgroundScanner.onScanResultReceived(intent)
            Log.i("ScanReceiver", "Scan results received: $scanResults")
        } catch (exception: BleScanException) {
            Log.e("ScanReceiver", "Failed to scan devices", exception)
        }
    }
}