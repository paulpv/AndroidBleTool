package com.github.paulpv.androidbletool.collections

import android.util.Log
import com.github.paulpv.androidbletool.ExpiringIterableLongSparseArray
import com.github.paulpv.androidbletool.Utils
import java.util.*

class SortableSet<V> constructor(comparator: Comparator<in V>? = null) {
    companion object {
        private val TAG = Utils.TAG(SortableSet::class.java)

        private const val LOG_ADD = true
        private const val LOG_REMOVE = true
    }

    private var mBackingTreeSet: TreeSet<V>? = null

    init {
        setSortByValueComparator(comparator)
    }

    @Suppress("unused")
    fun isEmpty(): Boolean = mBackingTreeSet!!.isEmpty()

    fun size(): Int = mBackingTreeSet!!.size

    fun setSortByValueComparator(comparator: Comparator<in V>?) {
        val newTreeSet = TreeSet(comparator)
        if (mBackingTreeSet != null) {
            newTreeSet.addAll(mBackingTreeSet!!)
        }
        mBackingTreeSet = newTreeSet
    }

    fun indexOf(item: V): Int {
        return mBackingTreeSet!!.indexOf(item)
    }

    private fun itemsToString(verbose: Boolean): String {
        val sb = StringBuilder()
            .append("[")
        getIteratorAt(0).forEach {
            sb.append("\n")
            if (it is ExpiringIterableLongSparseArray.ItemWrapperImpl<*>) {
                sb.append(it.toString(verbose))
            } else {
                sb.append(it)
            }
            sb.append(", ")
        }
        if (size() > 0) {
            sb.append("\n")
        }
        return sb.append("]")
            .toString()
    }

    /**
     * @param item
     * @return the positive index of the updated element, or the `-index - 1` index of the added element.
     */
    fun add(item: V): Int {
        if (LOG_ADD) {
            Log.e(TAG, "#FOO\n\n")
            Log.e(TAG, "#FOO add(${Utils.quote(item)})")
            Log.e(TAG, "#FOO add: BEFORE mBackingTreeSet(${size()})=${itemsToString(true)}")
            Log.e(TAG, "#FOO add: mBackingTreeSet!!.remove(item)")
        }
        val removed = mBackingTreeSet!!.remove(item)
        if (LOG_ADD) {
            Log.e(TAG, "#FOO add: removed=$removed")
            Log.e(TAG, "#FOO add: mBackingTreeSet!!.add(item)")
        }
        val added = mBackingTreeSet!!.add(item)
        if (LOG_ADD) {
            Log.e(TAG, "#FOO add: added=$added")
            Log.e(TAG, "#FOO add: indexOf(item)")
        }
        val indexOf = indexOf(item)
        if (LOG_ADD) {
            Log.e(TAG, "#FOO add: indexOf=$indexOf")
        }
        @Suppress("UnnecessaryVariable") val result = if (removed) indexOf else -indexOf - 1
        if (LOG_ADD) {
            Log.e(TAG, "#FOO add: result=$result")
            Log.e(TAG, "#FOO add:  AFTER mBackingTreeSet(${size()})=${itemsToString(true)}")
        }
        return result
    }

    fun clear() {
        mBackingTreeSet!!.clear()
    }

    operator fun contains(item: V): Boolean {
        return indexOf(item) >= 0
    }

    fun getAt(index: Int): V {
        //Log.e(TAG, "#FOO getAt($index)")
        return getIteratorAt(index).next()
    }

    private fun getIteratorAt(index: Int): MutableIterator<V> {
        @Suppress("NAME_SHADOWING") var index = index
        if (index < 0) {
            throw IndexOutOfBoundsException("index must be >= 0")
        }
        val size = size()
        if (index != 0 && index >= size) {
            throw IndexOutOfBoundsException("index must be < size() or 0")
        }
        val it = mBackingTreeSet!!.iterator()
        while (index-- > 0) {
            it.next()
        }
        return it
    }

    @Suppress("unused")
    fun remove(item: V): Int {
        //Log.e(TAG, "#FOO remove($item)")
        //
        // TODO:(pv) Use a removal strategy depending on the sort type
        //  Example: Knowing that we are tracking BLE devices, removals are typically going to happen because
        //  a device went out of range. IN GENERAL, devices that go out of range have weak RSSI values.
        //  If we are sorting by RSSI, then a good strategy to find the item to be removed faster would be to
        //  iterate the collection from weakest to strongest.
        //  If we are sorting by Device Address, the weakest RSSIs are randomly distributed, so a good strategy
        //  to find the item to be removed would be a binarySearch.
        //
        //  To do this, when setting the comparator, the caller could also provide a custom iterator.
        //  This is just an *IDEA* for optimizing the remove operation; for now we just iterate beginning to end...
        //
        var result: Int = -1
        var i = 0
        val it = mBackingTreeSet!!.iterator()
        while (it.hasNext()) {
            if (it.next() === item) {
                it.remove()
                result = i
                break
            }
            ++i
        }
        if (LOG_REMOVE) {
            Log.e(TAG, "#FOO remove: result=$result")
        }
        return result
    }

    fun removeAt(index: Int): V {
        if (LOG_REMOVE) {
            Log.e(TAG, "#FOO removeAt($index)")
        }
        val it = getIteratorAt(index)
        val item = it.next()
        it.remove()
        return item
    }
}
