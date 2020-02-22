package com.github.paulpv.androidbletool.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.TextView
import com.github.paulpv.androidbletool.R
import com.github.paulpv.androidbletool.Utils
import java.util.*
import java.util.concurrent.TimeUnit

class DevicesViewHolder(val context: Context, itemView: ViewGroup) :
    BindableViewHolder<DeviceInfo>(itemView) {

    //companion object {
    //    private val TAG = Utils.TAG(DevicesViewHolder::class.java)
    //}

    private val labelAddress: TextView = itemView.findViewById(R.id.labelAddress)
    private val labelAge: TextView = itemView.findViewById(R.id.labelAge)
    private val labelLastSeen: TextView = itemView.findViewById(R.id.labelLastSeen)
    private val labelTimeoutRemaining: TextView = itemView.findViewById(R.id.labelTimeoutRemaining)
    private val labelName: TextView = itemView.findViewById(R.id.labelName)
    private val labelRssiReal: TextView = itemView.findViewById(R.id.labelRssiReal)
    private val labelRssiAverage: TextView = itemView.findViewById(R.id.labelRssiAverage)

    @SuppressLint("SetTextI18n")
    override fun bindTo(item: DeviceInfo, clickListener: OnClickListener) {
        super.bindTo(item, clickListener)
        labelAddress.text = item.macAddress
        labelAge.text = "age=${Utils.getTimeDurationFormattedString(item.addedElapsedMillis)}"
        labelLastSeen.text = "seen=${Utils.getTimeDurationFormattedString(item.lastUpdatedElapsedMillis, TimeUnit.MINUTES)}"
        labelTimeoutRemaining.text = "remain=${Utils.getTimeDurationFormattedString(item.timeoutRemainingMillis, TimeUnit.MINUTES)}"
        labelName.text = item.name
        labelRssiReal.text = String.format(Locale.getDefault(), "real=%04d", item.signalStrengthRealtime)
        labelRssiAverage.text = String.format(Locale.getDefault(), "avg=%04d", item.signalStrengthSmoothed)
    }
}
