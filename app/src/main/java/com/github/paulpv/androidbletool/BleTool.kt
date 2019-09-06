package com.github.paulpv.androidbletool

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.livinglifetechway.quickpermissions_kotlin.runWithPermissions
import com.polidea.rxandroidble2.LogConstants
import com.polidea.rxandroidble2.LogOptions
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.exceptions.BleScanException
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanSettings

/**
 * To call from Java:
 * https://kotlinlang.org/docs/reference/java-to-kotlin-interop.html
 */
class BleTool(context: Context) {

    companion object {
        private const val TAG = "BleTool"

        private const val SCAN_REQUEST_CODE = 69
    }

    class BleToolApp : Application() {
        lateinit var bleTool: BleTool
            private set

        override fun onCreate() {
            super.onCreate()
            bleTool = BleTool(this)
        }
    }

    class BleScanReceiver : BroadcastReceiver() {
        companion object {
            fun newPendingIntent(context: Context, requestCode: Int): PendingIntent =
                Intent(context, BleScanReceiver::class.java).let {
                    PendingIntent.getBroadcast(context, requestCode, it, 0)
                }
        }

        override fun onReceive(context: Context, intent: Intent) {
            (context.applicationContext as BleToolApp?)?.bleTool!!.onScanResultReceived(intent)
        }
    }

    private val rxBleClient: RxBleClient = RxBleClient.create(context)
    private val callbackIntent = BleScanReceiver.newPendingIntent(context, SCAN_REQUEST_CODE)

    init {
        RxBleClient.updateLogOptions(
            LogOptions.Builder()
                .setLogLevel(LogConstants.INFO)
                .setMacAddressLogSetting(LogConstants.MAC_ADDRESS_FULL)
                .setUuidsLogSetting(LogConstants.UUIDS_FULL)
                .setShouldLogAttributeValues(true)
                .build()
        )
    }

    fun scan(activity: FragmentActivity) {
        activity.runWithPermissions(Manifest.permission.ACCESS_COARSE_LOCATION) {
            scan(true)
        }
    }

    fun scan(on: Boolean) {
        if (on) {
            try {
                val scanSettings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .build()

                val scanFilter = ScanFilter.Builder()
                    // add custom filters if needed
                    //...
                    .build()

                if (Build.VERSION.SDK_INT >= 26) {
                    rxBleClient.backgroundScanner.scanBleDeviceInBackground(
                        callbackIntent,
                        scanSettings,
                        scanFilter
                    )
                } else {
                    TODO("Start old fashioned scanning...")
                }
            } catch (scanException: BleScanException) {
                Log.e(TAG, "Failed to start background scan", scanException)
                // TODO:(pv) Learn how to emit this to observer...
                //showError(scanException)
            }
        } else {
            if (Build.VERSION.SDK_INT >= 26) {
                rxBleClient.backgroundScanner.stopBackgroundBleScan(callbackIntent)
            } else {
                TODO("Stop old fashioned scanning...")
            }
        }
    }

    fun onScanResultReceived(intent: Intent) {
        try {
            val scanResults = rxBleClient.backgroundScanner.onScanResultReceived(intent)
            Log.i(TAG, "Scan results received: $scanResults")
            // TODO:(pv) Save individual results in to expirable collection
            // TODO:(pv) Emit each add and remove (via timeout)
        } catch (exception: BleScanException) {
            Log.e(TAG, "Failed to scan devices", exception)
        }
    }
}