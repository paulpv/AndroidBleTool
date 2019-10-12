package com.github.paulpv.androidbletool;

import android.util.Log;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

@SuppressWarnings({"WeakerAccess", "unused"})
public class ListenerManager<T> {
    private static final String TAG = Utils.Companion.TAG("ListenerManager");

    @SuppressWarnings({"PointlessBooleanExpression", "ConstantConditions"})
    private static final boolean VERBOSE_LOG = false && BuildConfig.DEBUG;

    private final String mName;
    private final Set<T> mListeners;
    private final Set<T> mListenersToAdd;
    private final Set<T> mListenersToRemove;

    private boolean mIsTraversingListeners;

    public ListenerManager(@NonNull Object name) {
        this(ReflectionUtils.getShortClassName(name));
    }

    public ListenerManager(@NonNull String name) {
        mName = Utils.Companion.quote(Runtime.toNonNullNonEmpty(name, "name").trim());
        mListeners = new LinkedHashSet<>();
        mListenersToAdd = new LinkedHashSet<>();
        mListenersToRemove = new LinkedHashSet<>();
    }

    @NotNull
    @Override
    public String toString() {
        return "{ mName=" + mName + ", size()=" + size() + " }";
    }

    public int size() {
        int size;
        synchronized (mListeners) {
            Set<T> consolidated = new LinkedHashSet<>(mListeners);
            consolidated.addAll(mListenersToAdd);
            consolidated.removeAll(mListenersToRemove);
            size = consolidated.size();
        }
        /*
        if (VERBOSE_LOG)
        {
            PbLog.v(TAG, mName + " size() == " + size);
        }
        */
        return size;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean contains(T listener) {
        synchronized (mListeners) {
            return (mListeners.contains(listener) || mListenersToAdd.contains(listener)) &&
                    mListenersToRemove.contains(listener);
        }
    }

    public boolean attach(T listener) {
        if (VERBOSE_LOG) {
            Log.v(TAG, mName + " attach(...)");
        }

        if (listener == null) {
            return false;
        }

        synchronized (mListeners) {
            if (mListeners.contains(listener) || mListenersToAdd.contains(listener)) {
                return false;
            }

            if (mIsTraversingListeners) {
                mListenersToAdd.add(listener);
            } else {

                mListeners.add(listener);
                updateListeners();
            }
            return true;
        }
    }

    public boolean detach(T listener) {
        if (VERBOSE_LOG) {
            Log.v(TAG, mName + " detach(...)");
        }

        if (listener == null) {
            return false;
        }

        synchronized (mListeners) {
            if (!mListeners.contains(listener) && !mListenersToAdd.contains(listener)) {
                return false;
            }

            if (mIsTraversingListeners) {
                mListenersToRemove.add(listener);
            } else {
                mListeners.remove(listener);
                updateListeners();
            }
            return true;
        }
    }

    public void clear() {
        if (VERBOSE_LOG) {
            Log.v(TAG, mName + " clear()");
        }
        synchronized (mListeners) {
            mListenersToAdd.clear();
            if (mIsTraversingListeners) {
                mListenersToRemove.addAll(mListeners);
            } else {
                mListeners.clear();
                mListenersToRemove.clear();
            }
        }
    }

    public Set<T> beginTraversing() {
        if (VERBOSE_LOG) {
            Log.v(TAG, mName + " beginTraversing()");
        }
        synchronized (mListeners) {
            mIsTraversingListeners = true;
            return Collections.unmodifiableSet(mListeners);
        }
    }

    public void endTraversing() {
        if (VERBOSE_LOG) {
            Log.v(TAG, mName + " endTraversing()");
        }
        synchronized (mListeners) {
            updateListeners();
            mIsTraversingListeners = false;
        }
    }

    private void updateListeners() {
        if (VERBOSE_LOG) {
            Log.v(TAG, mName + " updateListeners()");
        }
        synchronized (mListeners) {
            Iterator<T> it = mListenersToAdd.iterator();
            while (it.hasNext()) {
                mListeners.add(it.next());
                it.remove();
            }
            it = mListenersToRemove.iterator();
            while (it.hasNext()) {
                mListeners.remove(it.next());
                it.remove();
            }

            onListenersUpdated(mListeners.size());
        }
    }

    protected void onListenersUpdated(int listenersSize) {
    }
}
