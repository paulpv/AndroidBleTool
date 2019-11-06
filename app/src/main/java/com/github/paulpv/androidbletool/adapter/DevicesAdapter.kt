package com.github.paulpv.androidbletool.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.paulpv.androidbletool.BluetoothUtils
import com.github.paulpv.androidbletool.ExpiringIterableLongSparseArray
import com.github.paulpv.androidbletool.R
import com.polidea.rxandroidble2.scan.ScanResult
import java.util.*

class DevicesAdapter(context: Context) :
    SortableAdapter<SortBy, ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>, DevicesViewHolder>(context, SortBy.SignalLevelRssi) {

    companion object {
        private val SORT_BY_ADDRESS = Comparator<ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>> { obj1, obj2 ->
            BluetoothUtils.macAddressStringToPrettyString(obj1.value.bleDevice.macAddress)
                .compareTo(BluetoothUtils.macAddressStringToPrettyString(obj2.value.bleDevice.macAddress))
        }

        private val SORT_BY_NAME = Comparator<ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>> { obj1, obj2 ->
            val result = (obj1.value.bleDevice.name ?: "").compareTo(obj2.value.bleDevice.name ?: "")
            if (result != 0) result else SORT_BY_ADDRESS.compare(obj1, obj2)
        }

        private val SORT_BY_STRENGTH = Comparator<ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>> { obj1, obj2 ->
            //
            // NOTE: Intentionally inverted to initially sort RSSIs **descending**.
            //
            val result = obj2.value.rssi.compareTo(obj1.value.rssi)
            if (result != 0) result else SORT_BY_ADDRESS.compare(obj1, obj2)
        }

        private val SORT_BY_AGE = Comparator<ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>> { obj1, obj2 ->
            val result = obj1.ageMillis.compareTo(obj2.ageMillis)
            if (result != 0) result else SORT_BY_ADDRESS.compare(obj1, obj2)
        }

        private val SORT_BY_TIMEOUT_REMAINING = Comparator<ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>> { obj1, obj2 ->
            val result = obj1.timeoutRemainingMillis.compareTo(obj2.timeoutRemainingMillis)
            if (result != 0) result else SORT_BY_ADDRESS.compare(obj1, obj2)
        }
    }

    override fun getComparator(sortBy: SortBy?, reversed: Boolean): Comparator<ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>> {
        val comparator: Comparator<ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>> = when (sortBy) {
            SortBy.Address -> SORT_BY_ADDRESS
            SortBy.Name -> SORT_BY_NAME
            SortBy.SignalLevelRssi -> SORT_BY_STRENGTH
            SortBy.Age -> SORT_BY_AGE
            SortBy.TimeoutRemaining -> SORT_BY_TIMEOUT_REMAINING
            else -> throw IllegalStateException("unhandled sortBy=$sortBy")
        }
        return if (reversed) Collections.reverseOrder(comparator) else comparator
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DevicesViewHolder {
        //Log.e(TAG, "onCreateViewHolder(...)")
        val viewGroup = inflate(R.layout.device_cell, parent) as ViewGroup
        return DevicesViewHolder(viewGroup)
    }

    private var recyclerView: RecyclerView? = null
    private var layoutManager: LinearLayoutManager? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        //Log.e(TAG, "onAttachedToRecyclerView(...)")
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        this.layoutManager = recyclerView.layoutManager as LinearLayoutManager?
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        //Log.e(TAG, "onDetachedFromRecyclerView(...)")
        super.onDetachedFromRecyclerView(recyclerView)
        autoUpdateVisibleItems(false)
        this.recyclerView = null
        this.layoutManager = null
    }

    private val runnableRefreshVisibleItems = Runnable {
        autoUpdateVisibleItems(true)
    }

    public fun autoUpdateVisibleItems(enable: Boolean) {
        //Log.e(TAG, "refreshVisibleItems($enable)")
        if (enable) {
            val positionStart = layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
            if (positionStart != RecyclerView.NO_POSITION) {
                val positionStop = layoutManager!!.findLastVisibleItemPosition()
                val itemCount = 1 + positionStop - positionStart
                //Log.e(TAG, "refreshVisibleItems: positionStart=$positionStart, positionStop=$positionStop, itemCount=$itemCount")
                //Log.e(TAG, "refreshVisibleItems: notifyItemRangeChanged($positionStart, $itemCount)")
                notifyItemRangeChanged(positionStart, itemCount)
            }
            recyclerView?.postDelayed(runnableRefreshVisibleItems, 1000)
        } else {
            recyclerView?.removeCallbacks(runnableRefreshVisibleItems)
        }
    }

    fun onResume() {
        autoUpdateVisibleItems(true)
    }

    fun onPause() {
        autoUpdateVisibleItems(false)
    }
}
