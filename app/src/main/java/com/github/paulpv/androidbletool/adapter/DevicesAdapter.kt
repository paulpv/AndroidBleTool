package com.github.paulpv.androidbletool.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.paulpv.androidbletool.BleScanResult
import com.github.paulpv.androidbletool.ExpiringIterableLongSparseArray
import com.github.paulpv.androidbletool.R
import java.util.*

class DevicesAdapter(context: Context) :
    SortableAdapter<SortBy, ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>, DevicesViewHolder>(context, SortBy.SignalLevelRssi) {

    companion object {
        //private val TAG = Utils.TAG(DevicesAdapter::class.java)

        private val SORT_BY_ADDRESS =
            Comparator<ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>>(fun(
                obj1: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>,
                obj2: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>
            ): Int {
                return obj1.value.scanResult.device.address.compareTo(obj2.value.scanResult.device.address)
            })

        private val SORT_BY_NAME =
            Comparator<ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>>(fun(
                obj1: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>,
                obj2: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>
            ): Int {
                val resultAddress = SORT_BY_ADDRESS.compare(obj1, obj2)
                if (resultAddress == 0) return 0
                val result = (obj1.value.scanResult.device.name ?: "").compareTo(obj2.value.scanResult.device.name ?: "")
                return if (result != 0) result else resultAddress
            })

        private val SORT_BY_STRENGTH =
            Comparator<ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>>(fun(
                obj1: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>,
                obj2: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>
            ): Int {
                //Log.e(TAG, "#FOO SORT_BY_STRENGTH obj1=${obj1.toString(false)}")
                //Log.e(TAG, "#FOO SORT_BY_STRENGTH obj2=${obj2.toString(false)}")
                val resultAddress = SORT_BY_ADDRESS.compare(obj1, obj2)
                //Log.e(TAG, "#FOO SORT_BY_STRENGTH resultAddress=$resultAddress")
                if (resultAddress == 0) return 0
                //
                // NOTE: Intentionally inverted to initially sort RSSIs **descending**.
                //
                val resultStrength = obj2.value.rssi.compareTo(obj1.value.rssi)
                //Log.e(TAG, "#FOO SORT_BY_STRENGTH resultStrength=$resultStrength")
                //val result = if (resultAddress == 0) 0 else if (resultStrength != 0) resultStrength else resultAddress
                val result = if (resultStrength != 0) resultStrength else resultAddress
                //Log.e(TAG, "#FOO SORT_BY_STRENGTH result=$result")
                return result
            })

        private val SORT_BY_AGE =
            Comparator<ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>>(fun(
                obj1: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>,
                obj2: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>
            ): Int {
                val resultAddress = SORT_BY_ADDRESS.compare(obj1, obj2)
                if (resultAddress == 0) return 0
                val result = obj1.ageMillis.compareTo(obj2.ageMillis)
                return if (result != 0) result else resultAddress
            })

        private val SORT_BY_TIMEOUT_REMAINING =
            Comparator<ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>>(fun(
                obj1: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>,
                obj2: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>
            ): Int {
                val resultAddress = SORT_BY_ADDRESS.compare(obj1, obj2)
                if (resultAddress == 0) return 0
                val result = obj1.timeoutRemainingMillis.compareTo(obj2.timeoutRemainingMillis)
                return if (result != 0) result else resultAddress
            })
    }

    override fun getComparator(sortBy: SortBy?, reversed: Boolean): Comparator<ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>> {
        val comparator: Comparator<ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>> = when (sortBy) {
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
        return DevicesViewHolder(context, viewGroup)
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

    fun autoUpdateVisibleItems(enable: Boolean) {
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

    /**
     * Items could have changed while the UI was not visible; rebuild it
     */
    fun onResume(iterator: Iterator<ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>>) {//, autoUpdate: Boolean) {
        clear()
        iterator.forEach {
            put(it, false)
        }
        notifyItemRangeInserted(0, itemCount)
    }

    fun onPause() {
        autoUpdateVisibleItems(false)
    }
}
