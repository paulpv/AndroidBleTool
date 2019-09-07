package com.github.paulpv.androidbletool

import android.os.Bundle
import android.util.Log
import com.polidea.rxandroidble2.exceptions.BleScanException
import com.polidea.rxandroidble2.scan.ScanResult
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : BleTool.BleToolObserverActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var bleTool: BleTool

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleTool = (applicationContext as BleTool.BleToolApp).bleTool
        setContentView(R.layout.activity_main)
        scan_start_btn.setOnClickListener { bleTool.scanAttach(this) }
        scan_stop_btn.setOnClickListener { bleTool.scanDetach(this) }
    }

    override fun onComplete() {
        Log.e(TAG, "onComplete")
    }

    override fun onSubscribe(d: Disposable) {
        Log.e(TAG, "onSubscribe: d=$d")
    }

    override fun onNext(scanResult: ScanResult) {
        Log.e(TAG, "onNext: scanResult=$scanResult")
    }

    override fun onError(e: Throwable) {
        Log.e(TAG, "onError: e=$e")
        when (e) {
            is BleScanException -> showError(e)
            else -> showSnackbarShort(e.message ?: "null")
        }
    }

}
