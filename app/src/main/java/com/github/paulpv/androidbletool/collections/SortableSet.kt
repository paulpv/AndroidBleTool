package com.github.paulpv.androidbletool.collections

import com.github.paulpv.androidbletool.ExpiringIterableLongSparseArray
import com.github.paulpv.androidbletool.Utils
import java.util.*

class SortableSet<E> constructor(comparator: Comparator<in E>? = null) {
    companion object {
        private val TAG = Utils.TAG(SortableSet::class.java)
    }

    private var mBackingTreeSet: TreeSet<E>? = null

    init {
        setSortByValueComparator(comparator)
    }

    @Suppress("unused")
    fun isEmpty(): Boolean = mBackingTreeSet!!.isEmpty()

    fun size(): Int = mBackingTreeSet!!.size

    fun setSortByValueComparator(comparator: Comparator<in E>?) {
        val newTreeSet = TreeSet(comparator)
        if (mBackingTreeSet != null) {
            newTreeSet.addAll(mBackingTreeSet!!)
        }
        mBackingTreeSet = newTreeSet
    }

    fun indexOf(item: E): Int {
        return mBackingTreeSet!!.indexOf(item)
    }

    private fun itemsToString(): String {
        val sb = StringBuilder()
            .append("[")
        getIteratorAt(0).forEach {
            sb.append("\n")
            if (it is ExpiringIterableLongSparseArray.ItemWrapperImpl<*>) {
                sb.append(it.toString(false))
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
    fun add(item: E): Int {
        //Log.e(TAG, "#FOO add(${Utils.quote(item)})")
        val added = mBackingTreeSet!!.add(item)
        val index = indexOfKnownExisting(item)
        return if (added) -index - 1 else index
    }

    fun clear() {
        mBackingTreeSet!!.clear()
    }

    operator fun contains(item: E): Boolean {
        return indexOf(item) >= 0
    }

    fun getAt(index: Int): E {
        //Log.e(TAG, "#FOO getAt($index)")
        return getIteratorAt(index).next()
    }

    private fun getIteratorAt(index: Int): MutableIterator<E> {
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
    fun remove(item: E): Int {
        //Log.e(TAG, "#FOO getAt($item)")
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
        var i = 0
        val it = mBackingTreeSet!!.iterator()
        while (it.hasNext()) {
            if (it.next() === item) {
                it.remove()
                return i
            }
            ++i
        }
        return -1
    }

    fun removeAt(index: Int): E {
        //Log.e(TAG, "#FOO removeAt($index)")
        val it = getIteratorAt(index)
        val item = it.next()
        it.remove()
        return item
    }
}
