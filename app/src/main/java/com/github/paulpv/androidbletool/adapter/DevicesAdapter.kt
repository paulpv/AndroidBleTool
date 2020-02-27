package com.github.paulpv.androidbletool.adapter

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.paulpv.androidbletool.BleScanResult
import com.github.paulpv.androidbletool.collections.ExpiringIterableLongSparseArray
import com.github.paulpv.androidbletool.R
import com.github.paulpv.androidbletool.utils.Utils
import com.github.paulpv.androidbletool.collections.SortedList
import java.util.*

class DevicesAdapter(var context: Context, initialSortBy: SortBy) : RecyclerView.Adapter<DevicesViewHolder>() {
    companion object {
        private val TAG = Utils.TAG(DevicesAdapter::class.java)

        private const val AUTO_UPDATE_ENABLE = false
        private const val LOG_AUTO_UPDATE = true

        private fun itemsToString(items: SortedList<DeviceInfo>): String {
            val sb = StringBuilder()
                .append('[')
            val size = items.size()
            for (i in 0 until size) {
                sb.append('\n').append(i).append(' ').append(items.get(i)).append(", ")
            }
            if (size > 0) {
                sb.append('\n')
            }
            return sb.append(']')
                .toString()
        }

        private const val LOG_ADD = false
        private const val LOG_REMOVE = false

        private const val LOG_GET_ITEM_FROM_HOLDER = false
        private const val LOG_GET_ITEM_BY_INDEX = false

        private const val LOG_INSERTED = false
        private const val LOG_MOVED = false
        private const val LOG_REMOVED = false

        private const val LOG_SORT_BY_ADDRESS = false
        private const val LOG_SORT_BY_NAME = false
        private const val LOG_SORT_BY_STRENGTH = false
        private const val LOG_SORT_BY_AGE = false
        private const val LOG_SORT_BY_TIMEOUT_REMAINING = false

        private val SORT_BY_ADDRESS = object : Comparator<DeviceInfo> {
            override fun compare(o1: DeviceInfo, o2: DeviceInfo): Int {
                @Suppress("ConstantConditionIf")
                if (LOG_SORT_BY_ADDRESS) {
                    Log.e(TAG, "SORT_BY_ADDRESS o1=$o1")
                    Log.e(TAG, "SORT_BY_ADDRESS o2=$o2")
                }
                val resultAddress = o1.macAddress.compareTo(o2.macAddress)
                @Suppress("ConstantConditionIf")
                if (LOG_SORT_BY_ADDRESS) {
                    Log.e(TAG, "SORT_BY_ADDRESS resultAddress=$resultAddress")
                }
                return resultAddress
            }

            override fun toString(): String {
                return "SORT_BY_ADDRESS"
            }
        }

        private val SORT_BY_NAME = object : Comparator<DeviceInfo> {
            override fun compare(o1: DeviceInfo, o2: DeviceInfo): Int {
                @Suppress("ConstantConditionIf")
                if (LOG_SORT_BY_NAME) {
                    Log.e(TAG, "SORT_BY_NAME o1=$o1")
                    Log.e(TAG, "SORT_BY_NAME o2=$o2")
                }
                val resultName = o1.name.compareTo(o2.name)
                @Suppress("ConstantConditionIf")
                if (LOG_SORT_BY_NAME) {
                    Log.e(TAG, "SORT_BY_NAME resultName=$resultName")
                }
                return resultName
            }

            override fun toString(): String {
                return "SORT_BY_NAME"
            }
        }

        private val SORT_BY_STRENGTH = object : Comparator<DeviceInfo> {
            override fun compare(o1: DeviceInfo, o2: DeviceInfo): Int {
                @Suppress("ConstantConditionIf")
                if (LOG_SORT_BY_STRENGTH) {
                    Log.e(TAG, "SORT_BY_STRENGTH o1=$o1")
                    Log.e(TAG, "SORT_BY_STRENGTH o2=$o2")
                }
                //
                // NOTE: Intentionally INVERTED obj2.compareTo(obj1), instead of normal obj1.compareTo(obj2),
                // to default sort RSSIs **DESCENDING** (greatest to least).
                //
                //val resultStrength = o2.signalStrengthSmoothed - o1.signalStrengthSmoothed
                val resultStrength = o2.signalStrengthSmoothed.compareTo(o1.signalStrengthSmoothed)
                @Suppress("ConstantConditionIf")
                if (LOG_SORT_BY_STRENGTH) {
                    Log.e(TAG, "SORT_BY_STRENGTH resultStrength=$resultStrength")
                }
                return resultStrength
            }

            override fun toString(): String {
                return "SORT_BY_STRENGTH"
            }
        }

        private val SORT_BY_AGE = object : Comparator<DeviceInfo> {
            override fun compare(o1: DeviceInfo, o2: DeviceInfo): Int {
                @Suppress("ConstantConditionIf")
                if (LOG_SORT_BY_AGE) {
                    Log.e(TAG, "SORT_BY_AGE o1=$o1")
                    Log.e(TAG, "SORT_BY_AGE o2=$o2")
                }
                val resultAge = o1.addedElapsedMillis.compareTo(o2.addedElapsedMillis)
                @Suppress("ConstantConditionIf")
                if (LOG_SORT_BY_AGE) {
                    Log.e(TAG, "SORT_BY_AGE resultAge=$resultAge")
                }
                return resultAge
            }

            override fun toString(): String {
                return "SORT_BY_AGE"
            }
        }

        private val SORT_BY_TIMEOUT_REMAINING = object : Comparator<DeviceInfo> {
            override fun compare(o1: DeviceInfo, o2: DeviceInfo): Int {
                @Suppress("ConstantConditionIf")
                if (LOG_SORT_BY_TIMEOUT_REMAINING) {
                    Log.e(TAG, "SORT_BY_TIMEOUT_REMAINING o1=$o1")
                    Log.e(TAG, "SORT_BY_TIMEOUT_REMAINING o2=$o2")
                }
                val resultTimeoutRemaining = o1.timeoutRemainingMillis.compareTo(o2.timeoutRemainingMillis)
                @Suppress("ConstantConditionIf")
                if (LOG_SORT_BY_TIMEOUT_REMAINING) {
                    Log.e(TAG, "SORT_BY_TIMEOUT_REMAINING resultTimeoutRemaining=$resultTimeoutRemaining")
                }
                return resultTimeoutRemaining
            }

            override fun toString(): String {
                return "SORT_BY_TIMEOUT_REMAINING"
            }
        }

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
    private val itemsMacAddressToIndex = mutableMapOf<String, Int>()
    private val itemsIndexToMacAddress = mutableListOf<String>()
    private lateinit var items: SortedList<DeviceInfo>

    //private lateinit var comparators: Array<out Comparator<DeviceInfo>>

    private var sortReversed: Boolean = false

    var sortBy: SortBy? = null
        set(value) {
            Log.d(TAG, "setSortBy(sortBy=$value)")

            if (sortBy == null || sortBy != value) {
                field = value
                sortReversed = false
            } else {
                sortReversed = !sortReversed
            }

            /*
            val comparators = mutableListOf<Comparator<DeviceInfo>>()
            //
            // Primary sortBy, Secondary Name, Tertiary Address
            // https://stackoverflow.com/a/15240794/252308
            //
            if (sortBy != SortBy.Address) {
                comparators.add(getComparator(SortBy.Address, sortReversed))
            }
            if (sortBy != SortBy.Name) {
                comparators.add(getComparator(SortBy.Name, sortReversed))
            }
            comparators.add(getComparator(sortBy, sortReversed))
            this.comparators = comparators.toTypedArray()
             */

            // TODO:(pv) Tweak copied SortedList allow manually resorting existing items
            // Until then, rebuild the list by removing all items and then adding them back
            val temp = mutableListOf<DeviceInfo>()
            items.beginBatchedUpdates()
            while (items.size() > 0) {
                //Log.e(TAG, "items.removeItemAt(0)")
                temp.add(items.removeItemAt(0))
            }
            //items.endBatchedUpdates()
            //items.beginBatchedUpdates()
            //Log.e(TAG, "items.addAll($temp)")
            items.addAll(temp)
            items.endBatchedUpdates()
        }

    private var eventListener: EventListener<DeviceInfo>? = null

    init {
        itemViewOnClickListener = View.OnClickListener { this@DevicesAdapter.onItemClicked(it) }
        items = SortedList(DeviceInfo::class.java, object : SortedList.SortedListAdapterCallback<DeviceInfo>(this) {
            override fun compare(o1: DeviceInfo?, o2: DeviceInfo?): Int {
                return getComparator(sortBy, sortReversed).compare(o1, o2)
            }

            /*
            override fun getComparators(): Array<out Comparator<DeviceInfo>>? {
                return this@DevicesAdapter.comparators
            }
            */

            override fun areItemsTheSame(item1: DeviceInfo?, item2: DeviceInfo?): Boolean {
                //Log.e(TAG, "areItemsTheSame: item1=$item1")
                //Log.e(TAG, "areItemsTheSame: item2=$item2")
                @Suppress("UnnecessaryVariable") val result = item1!!.macAddress == item2!!.macAddress
                //Log.e(TAG, "areItemsTheSame: result=$result")
                return result
            }

            override fun areContentsTheSame(oldItem: DeviceInfo?, newItem: DeviceInfo?): Boolean {
                //Log.e(TAG, "areContentsTheSame: oldItem=$oldItem")
                //Log.e(TAG, "areContentsTheSame: newItem=$newItem")
                @Suppress("UnnecessaryVariable") val result = oldItem == newItem
                //val result = oldItem!!.equals(newItem)
                //Log.e(TAG, "areContentsTheSame: result=$result")
                return result
            }

            override fun onInserted(position: Int, count: Int) {
                @Suppress("ConstantConditionIf")
                if (LOG_INSERTED) {
                    Log.e(TAG, "onInserted(position=$position, count=$count)")

                    Log.e(TAG, "onInserted: items($itemCount)=${itemsToString(items)}")

                    Log.e(TAG, "onInserted: BEFORE itemsMacAddressToIndex=$itemsMacAddressToIndex")
                    Log.e(TAG, "onInserted: BEFORE itemsIndexToMacAddress=$itemsIndexToMacAddress")
                }

                for (i in position until position + count) {
                    val item = getItemByIndex(i)
                    val macAddress = item.macAddress
                    @Suppress("ConstantConditionIf")
                    if (LOG_INSERTED) {
                        Log.e(TAG, "onInserted: macAddress=$macAddress, position=$i")
                    }

                    // Insert the item; items to the end are shifted one to the right
                    @Suppress("ConstantConditionIf")
                    if (LOG_INSERTED) {
                        Log.e(TAG, "onInserted: itemsIndexToMacAddress.add($i, $macAddress)")
                    }
                    itemsIndexToMacAddress.add(i, macAddress)
                }

                // Readjust the dictionary for the shifted items only
                val size = itemsIndexToMacAddress.size
                for (i in position until size) {
                    @Suppress("NAME_SHADOWING") val macAddress = itemsIndexToMacAddress[i]
                    @Suppress("ConstantConditionIf")
                    if (LOG_INSERTED) {
                        Log.e(TAG, "onInserted: itemsMacAddressToIndex[$macAddress] = $i")
                    }
                    itemsMacAddressToIndex[macAddress] = i
                }

                @Suppress("ConstantConditionIf")
                if (LOG_INSERTED) {
                    Log.e(TAG, "onInserted:  AFTER itemsMacAddressToIndex=$itemsMacAddressToIndex")
                    Log.e(TAG, "onInserted:  AFTER itemsIndexToMacAddress=$itemsIndexToMacAddress")
                }

                super.onInserted(position, count)
            }

            override fun onMoved(fromPosition: Int, toPosition: Int) {
                @Suppress("ConstantConditionIf")
                if (LOG_MOVED) {
                    Log.e(TAG, "onMoved(fromPosition=$fromPosition, toPosition=$toPosition)")
                }

                val item = getItemByIndex(toPosition)
                val macAddress = item.macAddress

                @Suppress("ConstantConditionIf")
                if (LOG_MOVED) {
                    Log.e(TAG, "onMoved: items($itemCount)=${itemsToString(items)}")

                    Log.e(TAG, "onMoved: macAddress=$macAddress, fromPosition=$fromPosition, toPosition=$toPosition")

                    Log.e(TAG, "onMoved: BEFORE itemsMacAddressToIndex=$itemsMacAddressToIndex")
                    Log.e(TAG, "onMoved: BEFORE itemsIndexToMacAddress=$itemsIndexToMacAddress")
                }

                @Suppress("ConstantConditionIf")
                if (LOG_MOVED) {
                    Log.e(TAG, "onMoved: itemsIndexToMacAddress.removeAt($fromPosition)")
                }
                itemsIndexToMacAddress.removeAt(fromPosition)

                @Suppress("ConstantConditionIf")
                if (LOG_MOVED) {
                    Log.e(TAG, "onMoved: itemsIndexToMacAddress.add($toPosition, $macAddress)")
                }
                itemsIndexToMacAddress.add(toPosition, macAddress)

                // Item could have moved from left to right or right to left
                // Readjust the dictionary for the shifted items only
                if (fromPosition < toPosition) {
                    @Suppress("ConstantConditionIf")
                    if (LOG_MOVED) {
                        Log.e(TAG, "onMoved: left ($fromPosition) -> right ($toPosition)")
                    }
                    // Item moved left to right: items from old position to new position are shifted left one position
                    // Readjust the dictionary for the shifted items only
                    for (i in fromPosition until toPosition + 1) {
                        @Suppress("NAME_SHADOWING") val macAddress = itemsIndexToMacAddress[i]
                        @Suppress("ConstantConditionIf")
                        if (LOG_MOVED) {
                            Log.e(TAG, "onMoved: itemsMacAddressToIndex[$macAddress] = $i")
                        }
                        itemsMacAddressToIndex[macAddress] = i
                    }
                } else { // fromPosition > toPosition
                    @Suppress("ConstantConditionIf")
                    if (LOG_MOVED) {
                        Log.e(TAG, "onMoved: left ($toPosition) <- right ($fromPosition)")
                    }
                    // Item moved right to left: items from new position to old position are shifted right one position
                    for (i in toPosition until fromPosition + 1) {
                        @Suppress("NAME_SHADOWING") val macAddress = itemsIndexToMacAddress[i]
                        @Suppress("ConstantConditionIf")
                        if (LOG_MOVED) {
                            Log.e(TAG, "onMoved: itemsMacAddressToIndex[$macAddress] = $i")
                        }
                        itemsMacAddressToIndex[macAddress] = i
                    }
                }

                @Suppress("ConstantConditionIf")
                if (LOG_MOVED) {
                    Log.e(TAG, "onMoved:  AFTER itemsMacAddressToIndex=$itemsMacAddressToIndex")
                    Log.e(TAG, "onMoved:  AFTER itemsIndexToMacAddress=$itemsIndexToMacAddress")
                }

                super.onMoved(fromPosition, toPosition)
            }

            override fun onRemoved(position: Int, count: Int) {
                @Suppress("ConstantConditionIf")
                if (LOG_REMOVED) {
                    Log.e(TAG, "onRemoved(position=$position, count=$count)")
                }

                if (position >= 0 && position < itemsIndexToMacAddress.size) {
                    @Suppress("ConstantConditionIf")
                    if (LOG_REMOVED) {
                        Log.e(TAG, "onRemoved: items($itemCount)=${itemsToString(items)}")

                        Log.e(TAG, "onRemoved: BEFORE itemsMacAddressToIndex=$itemsMacAddressToIndex")
                        Log.e(TAG, "onRemoved: BEFORE itemsIndexToMacAddress=$itemsIndexToMacAddress")
                    }

                    // Delete the item; items to the end are shifted one to the left
                    for (i in 0 until count) {
                        @Suppress("ConstantConditionIf")
                        if (LOG_REMOVED) {
                            Log.e(TAG, "onRemoved: itemsIndexToMacAddress.removeAt($position)")
                        }
                        val macAddress = itemsIndexToMacAddress.removeAt(position)

                        @Suppress("ConstantConditionIf")
                        if (LOG_REMOVED) {
                            Log.e(TAG, "onRemoved: itemsMacAddressToIndex.remove($macAddress)")
                        }
                        itemsMacAddressToIndex.remove(macAddress)
                    }

                    // Readjust the dictionary for the shifted items only
                    val size = itemsIndexToMacAddress.size
                    for (i in position until size) {
                        @Suppress("NAME_SHADOWING") val macAddress = itemsIndexToMacAddress[i]
                        @Suppress("ConstantConditionIf")
                        if (LOG_REMOVED) {
                            Log.e(TAG, "onRemoved: itemsMacAddressToIndex[$macAddress] = $i")
                        }
                        itemsMacAddressToIndex[macAddress] = i
                    }

                    @Suppress("ConstantConditionIf")
                    if (LOG_REMOVED) {
                        Log.e(TAG, "onRemoved:  AFTER itemsMacAddressToIndex=$itemsMacAddressToIndex")
                        Log.e(TAG, "onRemoved:  AFTER itemsIndexToMacAddress=$itemsIndexToMacAddress")
                    }
                }

                super.onRemoved(position, count)
            }
        })

        //
        // lateinit to create comparators
        //
        sortBy = initialSortBy
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
        itemsMacAddressToIndex.clear()
        itemsIndexToMacAddress.clear()
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

    /**
     * NOTE: SortedList items sorts items by a defined comparison, and SortedList.indexOf(...) is only a binary search that
     * assumes that sort order.
     * Ergo, you cannot sort by signalStrength and then expect to be able to reliably use indexOf(deviceInfo) to find the
     * index of a device with deviceInfo.macAddress.
     * You would only be able to reliably find the index of an element with the sorted field, in this case
     * deviceInfo.signalStrength.
     */
    private fun findIndexByMacAddress(macAddress: String): Int {
        @Suppress("UnnecessaryVariable") val index = itemsMacAddressToIndex[macAddress] ?: SortedList.INVALID_POSITION
        //Log.e(TAG, "findIndexByMacAddress: index=$index")
        return index
    }

    fun add(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>): Int {
        val deviceInfo = DeviceInfo.newInstance(item)
        @Suppress("ConstantConditionIf")
        if (LOG_ADD) {
            Log.e(TAG, "\n\n")
            //Log.e(TAG, "add($item)")
            Log.e(TAG, "add($deviceInfo)")
            Log.e(TAG, "add: BEFORE items($itemCount)=${itemsToString(items)}")
        }

        @Suppress("ConstantConditionIf")
        if (LOG_ADD) {
            Log.e(TAG, "add: indexExisting = findIndexByMacAddress(${deviceInfo.macAddress})")
        }
        val indexExisting = findIndexByMacAddress(deviceInfo.macAddress)
        @Suppress("ConstantConditionIf")
        if (LOG_ADD) {
            Log.e(TAG, "add: indexExisting=$indexExisting")
        }

        val indexAdded = if (indexExisting == SortedList.INVALID_POSITION) {
            @Suppress("ConstantConditionIf")
            if (LOG_ADD) {
                Log.e(TAG, "add: items.add($deviceInfo)")
            }
            items.add(deviceInfo)
        } else {
            @Suppress("ConstantConditionIf")
            if (LOG_ADD) {
                Log.e(TAG, "add: items.updateItemAt($indexExisting, $deviceInfo)")
            }
            // NOTE:(pv) Only re-sorts if compare != 0 !!!
            items.updateItemAt(indexExisting, deviceInfo)
            indexExisting
        }

        @Suppress("ConstantConditionIf")
        if (LOG_ADD) {
            Log.e(TAG, "add: AFTER items($itemCount)=${itemsToString(items)}")
            Log.e(TAG, "\n\n")
        }

        return indexAdded
    }

    fun remove(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>): Boolean {
        val deviceInfo = DeviceInfo.newInstance(item)
        @Suppress("ConstantConditionIf")
        if (LOG_REMOVE) {
            Log.e(TAG, "\n\n")
            //Log.e(TAG, "remove($item)")
            Log.e(TAG, "remove($deviceInfo)")
            Log.e(TAG, "remove: BEFORE items($itemCount)=${itemsToString(items)}")
        }

        @Suppress("ConstantConditionIf")
        if (LOG_REMOVE) {
            Log.e(TAG, "remove: items.remove($deviceInfo)")
        }
        val removed = items.remove(deviceInfo)

        @Suppress("ConstantConditionIf")
        if (LOG_REMOVE) {
            Log.e(TAG, "remove: AFTER items($itemCount)=${itemsToString(items)}")
            Log.e(TAG, "\n\n")
        }

        return removed
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
