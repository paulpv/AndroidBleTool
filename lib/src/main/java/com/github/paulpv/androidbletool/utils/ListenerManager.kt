package com.github.paulpv.androidbletool.utils

import android.util.Log
import com.github.paulpv.androidbletool.BuildConfig
import com.github.paulpv.androidbletool.utils.Utils.TAG
import java.util.*

open class ListenerManager<T>(name: String) {
    companion object {
        private val TAG = TAG(ListenerManager::class.java)

        @Suppress("SimplifyBooleanWithConstants")
        private val VERBOSE_LOG = false && BuildConfig.DEBUG
    }

    private val name: String = Utils.quote(RuntimeUtils.toNonNullNonEmpty(name, "name").trim { it <= ' ' })
    private val listeners: MutableSet<T>
    private val listenersToAdd: MutableSet<T>
    private val listenersToRemove: MutableSet<T>

    private var isTraversingListeners: Boolean = false

    @Suppress("unused")
    constructor(name: Any) : this(ReflectionUtils.getShortClassName(name))

    init {
        listeners = LinkedHashSet()
        listenersToAdd = LinkedHashSet()
        listenersToRemove = LinkedHashSet()
    }

    override fun toString(): String {
        return "{ name=$name, size()=${size()} }"
    }

    @Suppress("unused")
    fun isEmpty(): Boolean = size() == 0

    @Suppress("MemberVisibilityCanBePrivate")
    fun size(): Int {
        val size: Int
        synchronized(listeners) {
            val consolidated = LinkedHashSet(listeners)
            consolidated.addAll(listenersToAdd)
            consolidated.removeAll(listenersToRemove)
            size = consolidated.size
        }
        /*
        if (VERBOSE_LOG)
        {
            PbLog.v(TAG, "$name size() == $size");
        }
        */
        return size
    }

    operator fun contains(listener: T): Boolean {
        synchronized(listeners) {
            return (listeners.contains(listener) || listenersToAdd.contains(listener)) && listenersToRemove.contains(listener)
        }
    }

    fun attach(listener: T?): Boolean {
        if (VERBOSE_LOG) {
            Log.v(TAG, "$name attach(...)")
        }

        if (listener == null) {
            return false
        }

        synchronized(listeners) {
            if (listeners.contains(listener) || listenersToAdd.contains(listener)) {
                return false
            }

            if (isTraversingListeners) {
                listenersToAdd.add(listener)
            } else {

                listeners.add(listener)
                updateListeners()
            }
            return true
        }
    }

    fun detach(listener: T?): Boolean {
        if (VERBOSE_LOG) {
            Log.v(TAG, "$name detach(...)")
        }

        if (listener == null) {
            return false
        }

        synchronized(listeners) {
            if (!listeners.contains(listener) && !listenersToAdd.contains(listener)) {
                return false
            }

            if (isTraversingListeners) {
                listenersToRemove.add(listener)
            } else {
                listeners.remove(listener)
                updateListeners()
            }
            return true
        }
    }

    @Suppress("unused")
    fun clear() {
        if (VERBOSE_LOG) {
            Log.v(TAG, "$name clear()")
        }
        synchronized(listeners) {
            listenersToAdd.clear()
            if (isTraversingListeners) {
                listenersToRemove.addAll(listeners)
            } else {
                listeners.clear()
                listenersToRemove.clear()
            }
        }
    }

    fun beginTraversing(): Set<T> {
        if (VERBOSE_LOG) {
            Log.v(TAG, "$name beginTraversing()")
        }
        synchronized(listeners) {
            isTraversingListeners = true
            return Collections.unmodifiableSet(listeners)
        }
    }

    fun endTraversing() {
        if (VERBOSE_LOG) {
            Log.v(TAG, "$name endTraversing()")
        }
        synchronized(listeners) {
            updateListeners()
            isTraversingListeners = false
        }
    }

    private fun updateListeners() {
        if (VERBOSE_LOG) {
            Log.v(TAG, "$name updateListeners()")
        }
        synchronized(listeners) {
            var it: MutableIterator<T> = listenersToAdd.iterator()
            while (it.hasNext()) {
                listeners.add(it.next())
                it.remove()
            }
            it = listenersToRemove.iterator()
            while (it.hasNext()) {
                listeners.remove(it.next())
                it.remove()
            }

            onListenersUpdated(listeners.size)
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    protected fun onListenersUpdated(listenersSize: Int) {
    }
}
