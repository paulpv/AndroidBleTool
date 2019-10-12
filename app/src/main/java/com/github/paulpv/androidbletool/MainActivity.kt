package com.github.paulpv.androidbletool

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.polidea.rxandroidble2.exceptions.BleScanException
import com.polidea.rxandroidble2.scan.ScanResult
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), BleTool.BleToolActivity {

    companion object {
        private val TAG = Utils.TAG(MainActivity::class.java)
    }

    private var bleTool: BleTool? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleTool = BleTool.getInstance(this)
        setContentView(R.layout.activity_main)
        button_scan_start.setOnClickListener { bleTool?.persistentScanningEnable(true) }
        button_scan_stop.setOnClickListener { bleTool?.persistentScanningEnable(false) }
    }

    /*
    override fun onStart() {
        super.onStart()
        //bleTool?.onStart(this)
        //bleTool?.attach(this)
    }

    override fun onStop() {
        super.onStop()
        //bleTool?.onStop()
        //bleTool?.detach(this)
    }
    */

    /*
    override fun onComplete() {
        Log.e(TAG, "onComplete")
    }

    override fun onSubscribe(d: Disposable) {
        Log.e(TAG, "onSubscribe: d=$d")
    }

    override fun onNext(scanResult: ScanResult) {
        Log.e(TAG, "onNext: persistentScanningElapsedMillis=${bleTool?.persistentScanningElapsedMillis} scanResult=${scanResult}")
    }

    override fun onError(e: Throwable) {
        Log.e(TAG, "onError: e=$e")
        when (e) {
            is BleScanException -> showError(e)
            else -> showSnackbarShort(e.message ?: "null")
        }
    }
    */
}
