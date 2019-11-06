package com.github.paulpv.androidbletool.adapter

import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.TextView
import com.github.paulpv.androidbletool.BuildConfig
import com.github.paulpv.androidbletool.ExpiringIterableLongSparseArray
import com.github.paulpv.androidbletool.R
import com.polidea.rxandroidble2.scan.ScanResult
import java.util.*

class DevicesViewHolder internal constructor(itemView: ViewGroup) :
    BindableViewHolder<ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>>(itemView) {
    //companion object {
    //    private val TAG = Utils.TAG(DevicesViewHolder::class.java)
    //}

    private val labelAddress: TextView = itemView.findViewById(R.id.labelAddress)
    private val labelAge: TextView = itemView.findViewById(R.id.labelAge)
    private val labelTimeoutRemaining: TextView = itemView.findViewById(R.id.labelTimeoutRemaining)
    private val labelName: TextView = itemView.findViewById(R.id.labelName)
    private val labelRssiReal: TextView = itemView.findViewById(R.id.labelRssiReal)
    private val labelRssiAverage: TextView = itemView.findViewById(R.id.labelRssiAverage)

    override fun bindTo(item: ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>, clickListener: OnClickListener) {
        super.bindTo(item, clickListener)

        val scanResult = item.value

        val signalStrengthRealtime = scanResult.rssi
        val signalStrengthSmoothed = -1//item.signalLevelRssiSmoothed

        val bleDevice = scanResult.bleDevice

        labelAddress.text = bleDevice.macAddress
        val simplified = !(false && BuildConfig.DEBUG)
        labelAge.text = if (simplified) String.format(Locale.getDefault(), "age=%ds", item.ageMillis / 1000)
        else String.format(Locale.getDefault(), "age=%.3fs", item.ageMillis / 1000.0)
        labelTimeoutRemaining.text = if (simplified) String.format(Locale.getDefault(), "remain=%ds", item.timeoutRemainingMillis / 1000)
        else String.format(Locale.getDefault(), "remain=%.3fs", item.timeoutRemainingMillis / 1000.0)
        labelName.text = bleDevice.name
        labelRssiReal.text = String.format(Locale.getDefault(), "real=%04d", signalStrengthRealtime)
        labelRssiAverage.text = String.format(Locale.getDefault(), "avg=%04d", signalStrengthSmoothed)
    }
}
