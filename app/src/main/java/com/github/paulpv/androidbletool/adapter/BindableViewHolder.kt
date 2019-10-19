package com.github.paulpv.androidbletool.adapter

import android.view.View
import android.view.View.OnClickListener

import androidx.recyclerview.widget.RecyclerView

abstract class BindableViewHolder<T> internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
    open fun bindTo(item: T, clickListener: OnClickListener) {
        //int position = getLayoutPosition();
        //PbLog.v(TAG, "bindTo: position=" + position);
        itemView.setOnClickListener(clickListener)
        itemView.tag = this
    }
}
