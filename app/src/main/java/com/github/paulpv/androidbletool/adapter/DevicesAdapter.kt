package com.github.paulpv.androidbletool.adapter

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import androidx.recyclerview.widget.SortedListAdapterCallback
import com.github.paulpv.androidbletool.BleScanResult
import com.github.paulpv.androidbletool.ExpiringIterableLongSparseArray
import com.github.paulpv.androidbletool.R
import com.github.paulpv.androidbletool.Utils
import java.util.*

class DevicesAdapter(var context: Context, initialSortBy: SortBy) : RecyclerView.Adapter<DevicesViewHolder>() {
    companion object {
        private val TAG = Utils.TAG(DevicesAdapter::class.java)

        private const val AUTO_UPDATE_ENABLE = false
        private const val LOG_AUTO_UPDATE = true

        private const val LOG_GET_ITEM_FROM_HOLDER = false
        private const val LOG_GET_ITEM_BY_INDEX = false

        private const val LOG_SORT_BY_ADDRESS = false
        private const val LOG_SORT_BY_STRENGTH = false

        private val SORT_BY_ADDRESS = Comparator<DeviceInfo>(fun(
            obj1: DeviceInfo,
            obj2: DeviceInfo
        ): Int {
            @Suppress("ConstantConditionIf")
            if (LOG_SORT_BY_ADDRESS) {
                Log.e(TAG, "SORT_BY_ADDRESS obj1=$obj1")
                Log.e(TAG, "SORT_BY_ADDRESS obj2=$obj2")
            }
            val resultAddress = obj1.macAddress.compareTo(obj2.macAddress)
            @Suppress("ConstantConditionIf")
            if (LOG_SORT_BY_ADDRESS) {
                Log.e(TAG, "SORT_BY_ADDRESS resultAddress=$resultAddress")
            }
            return resultAddress
        })

        private val SORT_BY_NAME = Comparator<DeviceInfo>(fun(
            obj1: DeviceInfo,
            obj2: DeviceInfo
        ): Int {
            val resultName = obj1.name.compareTo(obj2.name)
            return resultName
        })

        private val SORT_BY_STRENGTH =
            Comparator<DeviceInfo>(fun(
                obj1: DeviceInfo,
                obj2: DeviceInfo
            ): Int {
                @Suppress("ConstantConditionIf")
                if (LOG_SORT_BY_STRENGTH) {
                    Log.e(TAG, "SORT_BY_STRENGTH obj1=$obj1")
                    Log.e(TAG, "SORT_BY_STRENGTH obj2=$obj2")
                }
                //
                // NOTE: Intentionally INVERTED (obj2 - obj1, instead of normal obj1 - obj2) to default sort RSSIs **DESCENDING** (greatest to least).
                //
                val resultStrength = obj2.signalStrengthSmoothed - obj1.signalStrengthSmoothed
                @Suppress("ConstantConditionIf")
                if (LOG_SORT_BY_STRENGTH) {
                    Log.e(TAG, "SORT_BY_STRENGTH resultStrength=$resultStrength")
                }
                return resultStrength
            })

        private val SORT_BY_AGE =
            Comparator<DeviceInfo>(fun(
                obj1: DeviceInfo,
                obj2: DeviceInfo
            ): Int {
                val resultAge = obj1.addedElapsedMillis.compareTo(obj2.addedElapsedMillis)
                return resultAge
            })

        private val SORT_BY_TIMEOUT_REMAINING =
            Comparator<DeviceInfo>(fun(
                obj1: DeviceInfo,
                obj2: DeviceInfo
            ): Int {
                val resultRemaining = obj1.timeoutRemainingMillis.compareTo(obj2.timeoutRemainingMillis)
                return resultRemaining
            })

        private fun getComparator(sortBy: SortBy?, reversed: Boolean): Comparator<DeviceInfo> {
            val comparator: Comparator<DeviceInfo> = when (sortBy) {
                SortBy.Address -> SORT_BY_ADDRESS
                SortBy.Name -> SORT_BY_NAME
                SortBy.SignalLevelRssi -> SORT_BY_STRENGTH
                SortBy.Age -> SORT_BY_AGE
                SortBy.TimeoutRemaining -> SORT_BY_TIMEOUT_REMAINING
                else -> throw IllegalStateException("unhandled sortBy=$sortBy")
            }
            return if (reversed) Collections.reverseOrder(comparator) else comparator
        }
    }

    interface EventListener<T> {
        fun onItemSelected(item: T)
    }

    private val layoutInflater: LayoutInflater = LayoutInflater.from(context)
    private val itemViewOnClickListener: View.OnClickListener
    private var items: SortedList<DeviceInfo>

    var sortBy: SortBy = initialSortBy
        set(value) {
            Log.d(TAG, "setSortBy(sortBy=$value)")

            if (sortBy != value) {
                field = value
                sortReversed = false
            } else {
                sortReversed = !sortReversed
            }

            val temp = mutableListOf<DeviceInfo>()
            while (items.size() > 0) {
                temp.add(items.removeItemAt(0))
            }
            items.addAll(temp)
        }

    private var sortReversed: Boolean = false

    private var eventListener: EventListener<DeviceInfo>? = null

    init {
        itemViewOnClickListener = View.OnClickListener { this@DevicesAdapter.onItemClicked(it) }
        items = SortedList(DeviceInfo::class.java, object : SortedListAdapterCallback<DeviceInfo>(this) {
            override fun compare(
                o1: DeviceInfo?,
                o2: DeviceInfo?
            ): Int {
            }

            override fun areItemsTheSame(
                item1: DeviceInfo?,
                item2: DeviceInfo?
            ): Boolean {
                val result = item1!!.macAddress == item2!!.macAddress
                return result
            }

            override fun areContentsTheSame(
                oldItem: DeviceInfo?,
                newItem: DeviceInfo?
            ): Boolean {
                val result = oldItem == newItem
                return result
            }
        })
    }

    /**
     * Convenience method for [LayoutInflater.from].[LayoutInflater.inflate]
     */
    private fun inflate(@Suppress("SameParameterValue") @LayoutRes resource: Int, root: ViewGroup?): View {
        return layoutInflater.inflate(resource, root, false)
    }

    fun setEventListener(eventListener: EventListener<DeviceInfo>) {
        this.eventListener = eventListener
    }

    private fun onItemClicked(v: View) {
        if (eventListener != null) {
            val holder = v.tag as BindableViewHolder<*>
            val item = getItemFromHolder(holder)
            eventListener!!.onItemSelected(item)
        }
    }

    //
    //
    //

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DevicesViewHolder {
        //Log.e(TAG, "onCreateViewHolder(...)")
        val viewGroup = inflate(R.layout.device_cell, parent) as ViewGroup
        return DevicesViewHolder(context, viewGroup)
    }

    override fun onBindViewHolder(holder: DevicesViewHolder, position: Int) {
        //Log.e(TAG, "onBindViewHolder(...)")
        val item = getItemByIndex(position)
        holder.bindTo(item, itemViewOnClickListener)
    }

    //
    //
    //

    private fun getItemFromHolder(holder: BindableViewHolder<*>): DeviceInfo {
        val adapterPosition = holder.adapterPosition
        val layoutPosition = holder.layoutPosition
        @Suppress("ConstantConditionIf")
        if (LOG_GET_ITEM_FROM_HOLDER) {
            Log.e(TAG, "getItemFromHolder: $adapterPosition")
            Log.e(TAG, "getItemFromHolder: $layoutPosition")
        }
        return getItemByIndex(adapterPosition)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun getItemByIndex(index: Int): DeviceInfo {
        @Suppress("ConstantConditionIf")
        if (LOG_GET_ITEM_BY_INDEX) {
            Log.e(TAG, "getItemByIndex($index)")
        }
        val item = items.get(index)
        @Suppress("ConstantConditionIf")
        if (LOG_GET_ITEM_BY_INDEX) {
            Log.e(TAG, "getItemByIndex: $item")
        }
        return item
    }

    //
    //
    //

    override fun getItemCount(): Int {
        return items.size()
    }

    fun clear() {
        items.clear()
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun addAll(iterator: Iterator<ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>>) {
        items.beginBatchedUpdates()
        iterator.forEach {
            add(it)
        }
        items.endBatchedUpdates()
    }

    private fun itemsToString(verbose: Boolean): String {
        val sb = StringBuilder()
            .append("[")
        val size = items.size()
        for (i in 0 until size) {
            val deviceInfo = items.get(i)
            sb.append("\n").append(i).append(" ").append(deviceInfo).append(", ")
        }
        if (size > 0) {
            sb.append("\n")
        }
        return sb.append("]")
            .toString()
    }

    @Suppress("PrivatePropertyName")
    private val LOG_ADD = false

    /**
     * NOTE: SortedList items sorts items by a defined comparison, and SortedList.indexOf(...) is only a binary search assuming that sort order.
     * Example: You cannot sort by signalStrength and then expect to be able to reliably use indexOf(deviceInfo) to find the index of a device
     * with deviceInfo.macAddress; you would only find the index of an element with deviceInfo.signalStrength.
     * Thus, I am currently implementing a silly linear search.
     * TODO:(pv) Implement a faster search to find the index of a given macAddress
     */
    private fun findIndexByMacAddress(macAddress: String): Int {
        val size = items.size()
        for (i in 0 until size) {
            val deviceInfo = items.get(i)
            if (deviceInfo.macAddress == macAddress) {
                return i
            }
        }
        return SortedList.INVALID_POSITION
    }

    fun add(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        val deviceInfo = DeviceInfo.newInstance(item)
        @Suppress("ConstantConditionIf")
        if (LOG_ADD) {
            Log.e(TAG, "\n\n")
            //Log.e(TAG, "add($item)")
            Log.e(TAG, "add($deviceInfo)")
            Log.e(TAG, "add: BEFORE items($itemCount)=${itemsToString(true)}")
        }
        //Log.e(TAG, "add: indexExisting = items.indexOf($deviceInfo)")
        val indexExisting = findIndexByMacAddress(deviceInfo.macAddress)
        //Log.e(TAG, "add: indexExisting=$indexExisting")
        if (indexExisting == SortedList.INVALID_POSITION) {
            if (LOG_ADD) {
                Log.e(TAG, "add: items.add($deviceInfo)")
            }
            items.add(deviceInfo)
        } else {
            if (LOG_ADD) {
                Log.e(TAG, "add: items.updateItemAt($indexExisting, $deviceInfo)")
            }
            // NOTE:(pv) Only re-sorts if compare != 0!!!
            items.updateItemAt(indexExisting, deviceInfo)
        }
        if (LOG_ADD) {
            Log.e(TAG, "add: AFTER items($itemCount)=${itemsToString(true)}")
            Log.e(TAG, "\n\n")
        }
    }

    @Suppress("PrivatePropertyName")
    private val LOG_REMOVE = true

    fun remove(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        if (LOG_REMOVE) {
            Log.e(TAG, "\n\n")
            Log.e(TAG, "remove(${Utils.quote(item)})")
            Log.e(TAG, "remove: BEFORE items($itemCount)=${itemsToString(true)}")
        }
        val deviceInfo = DeviceInfo.newInstance(item)
        if (LOG_REMOVE) {
            Log.e(TAG, "remove: items.remove($deviceInfo)")
        }
        items.remove(deviceInfo)
        if (LOG_REMOVE) {
            Log.e(TAG, "remove: AFTER items($itemCount)=${itemsToString(true)}")
            Log.e(TAG, "\n\n")
        }
    }

    //
    //
    //

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
        @Suppress("ConstantConditionIf")
        if (!AUTO_UPDATE_ENABLE) {
            return
        }
        Log.e(TAG, "autoUpdateVisibleItems($enable)")
        if (enable) {
            val positionStart = layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
            if (positionStart != RecyclerView.NO_POSITION) {
                val positionStop = layoutManager!!.findLastVisibleItemPosition()
                val itemCount = 1 + positionStop - positionStart
                @Suppress("ConstantConditionIf")
                if (LOG_AUTO_UPDATE) {
                    Log.e(TAG, "refreshVisibleItems: positionStart=$positionStart, positionStop=$positionStop, itemCount=$itemCount")
                    Log.e(TAG, "refreshVisibleItems: notifyItemRangeChanged($positionStart, $itemCount)")
                }
                notifyItemRangeChanged(positionStart, itemCount)
            }
            recyclerView?.postDelayed(runnableRefreshVisibleItems, 1000)
        } else {
            recyclerView?.removeCallbacks(runnableRefreshVisibleItems)
        }
    }

    //
    //
    //

    /**
     * Items could have changed while the UI was not visible; rebuild it from scratch
     */
    fun onResume(iterator: Iterator<ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>>, autoUpdate: Boolean) {
        clear()
        addAll(iterator)
        @Suppress("ConstantConditionIf")
        if (AUTO_UPDATE_ENABLE) {
            if (autoUpdate) {
                autoUpdateVisibleItems(true)
            }
        }
    }

    fun onPause() {
        @Suppress("ConstantConditionIf")
        if (AUTO_UPDATE_ENABLE) {
            autoUpdateVisibleItems(false)
        }
    }
}
