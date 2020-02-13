package com.github.paulpv.androidbletool.adapter

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.github.paulpv.androidbletool.Utils
import com.github.paulpv.androidbletool.collections.SortableSet
import java.util.*

/**
 * @param <S>  Enum type to sort by
 * @param <T>  Item type to sort
 * @param <VH> ViewHolder type for Item
</VH></T></S> */
abstract class SortableAdapter<S : Enum<*>, T, VH : BindableViewHolder<T>> (val context: Context, initialSortBy: S?) :
    RecyclerView.Adapter<VH>() {
    companion object {
        private val TAG = Utils.TAG(SortableAdapter::class.java)
    }

    private val layoutInflater: LayoutInflater = LayoutInflater.from(context)
    private val itemViewOnClickListener: View.OnClickListener
    private val items: SortableSet<T>

    var sortBy: S? = null
        set(sortBy) {
            Log.d(TAG, "setSortBy(sortBy=$sortBy)")

            if (this.sortBy == null || this.sortBy != sortBy) {
                field = sortBy
                sortReversed = false
            } else {
                sortReversed = !sortReversed
            }

            val comparator = getComparator(this.sortBy, sortReversed)
            Log.v(TAG, "setSortBy: comparator=$comparator")

            items.setSortByValueComparator(comparator)
            notifyItemRangeChanged(0, items.size())
        }

    private var sortReversed: Boolean = false

    private var eventListener: EventListener<T>? = null

    private var lastRemovedItem: T? = null

    interface EventListener<T> {
        fun onItemSelected(item: T)
    }

    init {
        itemViewOnClickListener = View.OnClickListener { this@SortableAdapter.onItemClicked(it) }
        items = SortableSet()
        sortBy = initialSortBy
        //setHasStableIds(true)
    }

    fun setEventListener(eventListener: EventListener<T>) {
        this.eventListener = eventListener
    }

    private fun onItemClicked(v: View) {
        if (eventListener != null) {
            val holder = v.tag as BindableViewHolder<*>
            val item = getItemFromHolder(holder)
            eventListener!!.onItemSelected(item)
        }
    }

    protected abstract fun getComparator(sortBy: S?, reversed: Boolean): Comparator<T>

    override fun getItemCount(): Int {
        return items.size()
    }

    /**
     * Convenience method for [LayoutInflater.from].[LayoutInflater.inflate]
     */
    internal fun inflate(@LayoutRes resource: Int, root: ViewGroup?): View {
        return layoutInflater.inflate(resource, root, false)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItemByIndex(position)
        holder.bindTo(item, itemViewOnClickListener)
    }

    fun clear() {
        Log.e(TAG, "clear()")
        lastRemovedItem = null
        val size = itemCount
        //Log.e(TAG, "clear: size=$size")
        //Log.e(TAG, "clear: mItems.clear()")
        items.clear()
        //Log.e(TAG, "clear: notifyItemRangeRemoved(0, $size)')
        notifyItemRangeRemoved(0, size)
    }

    private fun getItemFromHolder(holder: BindableViewHolder<*>): T {
        return getItemByIndex(holder.adapterPosition)
        //return getItemByIndex(holder.getLayoutPosition())//.getAdapterPosition())
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun getItemByIndex(index: Int): T {
        return items.getAt(index)
    }

    fun put(item: T, notify: Boolean = true): Int {
        //Log.e(TAG, "#FOO put(item=${(item as ExpiringIterableLongSparseArray.ItemWrapperImpl<*>).toString(false)}, notify=$notify)")
        var position = items.add(item)
        //Log.e(TAG, "#FOO put: position=$position, itemCount=$itemCount")
        if (notify) {
            if (position > -1) {
                //Log.e(TAG, "#FOO put: notifyItemChanged(position=$position)")
                notifyItemChanged(position)
            } else {
                position = -position - 1
                //Log.e(TAG, "#FOO put: notifyItemInserted(position=$position)")
                notifyItemInserted(position)
            }
        }
        return position
    }

    /**
     * @param item      the item to remove
     * @param allowUndo true to allow the item to be restored in [.undoLastRemove]
     * @return the item that was removed, or null if no item was removed
     */
    fun remove(item: T, notify: Boolean = true, allowUndo: Boolean): T? {
        //Log.e(TAG, "#FOO remove(item=$item, notify=$notify, allowUndo=$allowUndo)")
        val position = items.indexOf(item)
        if (position < 0) {
            //Log.e(TAG, "#FOO remove: position($position) < 0; ignoring")
            return null
        }
        //Log.e(TAG, "#FOO remove: mItems.removeAt(position=$position)")
        val removed = items.removeAt(position)
        //Log.e(TAG, "#FOO remove: removed=$removed")
        if (notify) {
            //Log.e(TAG, "#FOO remove: notifyItemRemoved(position=$position)")
            notifyItemRemoved(position)
        }
        if (allowUndo) {
            lastRemovedItem = removed
        }
        return removed
    }

    /**
     * @return position that the item was restored to, or -1 if no item was restored
     */
    fun undoLastRemove(): Int {
        val position: Int
        if (lastRemovedItem != null) {
            position = put(lastRemovedItem!!)
            lastRemovedItem = null
        } else {
            position = -1
        }
        return position
    }
}
