package com.github.paulpv.androidbletool.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.TextView
import com.github.paulpv.androidbletool.BleScanResult
import com.github.paulpv.androidbletool.ExpiringIterableLongSparseArray
import com.github.paulpv.androidbletool.R
import com.github.paulpv.androidbletool.Utils
import java.util.*

class DevicesViewHolder(val context: Context, itemView: ViewGroup) :
    BindableViewHolder<ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>>(itemView) {

    //companion object {
    //    private val TAG = Utils.TAG(DevicesViewHolder::class.java)
    //}

    private val labelAddress: TextView = itemView.findViewById(R.id.labelAddress)
    private val labelAge: TextView = itemView.findViewById(R.id.labelAge)
    private val labelTimeoutRemaining: TextView = itemView.findViewById(R.id.labelTimeoutRemaining)
    private val labelName: TextView = itemView.findViewById(R.id.labelName)
    private val labelRssiReal: TextView = itemView.findViewById(R.id.labelRssiReal)
    private val labelRssiAverage: TextView = itemView.findViewById(R.id.labelRssiAverage)

    @SuppressLint("SetTextI18n")
    override fun bindTo(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>, clickListener: OnClickListener) {
        super.bindTo(item, clickListener)

        val bleScanResult = item.value
        val scanResult = bleScanResult.scanResult

        val signalStrengthRealtime = scanResult.rssi
        val signalStrengthSmoothed = bleScanResult.rssiSmoothed

        val bleDevice = scanResult.device

        labelAddress.text = bleDevice.address

        //@Suppress("SimplifyBooleanWithConstants") val simplified = !(false && BuildConfig.DEBUG)

        labelAge.text = "age=${Utils.getTimeDurationFormattedString(item.ageMillis)}"
        labelTimeoutRemaining.text = "remain=${Utils.getTimeDurationFormattedString(item.timeoutRemainingMillis)}"
        labelName.text = bleDevice.name
        labelRssiReal.text = String.format(Locale.getDefault(), "real=%04d", signalStrengthRealtime)
        labelRssiAverage.text = String.format(Locale.getDefault(), "avg=%04d", signalStrengthSmoothed)
    }
}
