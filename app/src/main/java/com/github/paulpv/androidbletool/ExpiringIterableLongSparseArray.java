package com.github.paulpv.androidbletool;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Iterator;
import java.util.NoSuchElementException;

@SuppressWarnings({"WeakerAccess", "unused"})
public class ExpiringIterableLongSparseArray<V> {
    private static final String TAG = Utils.Companion.TAG(ExpiringIterableLongSparseArray.class);

    private static final boolean VERBOSE_LOG_START = false;
    private static final boolean VERBOSE_LOG_STOP = false;
    private static final boolean VERBOSE_LOG_CLEAR = false;
    private static final boolean VERBOSE_LOG_PUT = false;
    private static final boolean VERBOSE_LOG_UPDATE = false;
    private static final boolean VERBOSE_LOG_EXPIRE = false;
    private static final boolean VERBOSE_LOG_REMOVE = false;

    public static final int DEFAULT_EXPIRATION_TIMEOUT_MILLIS = 30 * 1000;

    public interface ItemWrapper<V> {
        long getKey();

        V getValue();

        long getAddedUptimeMillis();

        long getAgeMillis();

        long getTimeoutMillis();

        long getTimeoutRemainingMillis();
    }

    /**
     * Adds internal "update" method to ItemWrapper
     *
     * @param <V>
     */
    public static class ItemWrapperImpl<V> implements ItemWrapper<V> {
        private final Long mKey;
        private final long mAddedUptimeMillis;

        private V mValue;
        private long mTimeoutMillis;

        public ItemWrapperImpl(long key, V value, long timeoutMillis) {
            mKey = key;
            mAddedUptimeMillis = SystemClock.uptimeMillis();
            update(value, timeoutMillis);
        }

        @NonNull
        @Override
        public String toString() {
            return ReflectionUtils.getShortClassName(this) + "@" + Integer.toHexString(hashCode()) +
                    "{ getKey()=" + getKey() +
                    ", getValue()=" + getValue() +
                    ", getAddedUptimeMillis()=" + getAddedUptimeMillis() +
                    ", getAgeMillis()=" + getAgeMillis() +
                    ", getTimeoutMillis()=" + getTimeoutMillis() +
                    ", getTimeoutRemainingMillis()=" + getTimeoutRemainingMillis() +
                    " }";
        }

        void update(V value, long timeoutMillis) {
            mValue = value;
            mTimeoutMillis = timeoutMillis;
        }

        @Override
        public long getKey() {
            return mKey;
        }

        @Override
        public V getValue() {
            return mValue;
        }

        @Override
        public long getAddedUptimeMillis() {
            return mAddedUptimeMillis;
        }

        @Override
        public long getAgeMillis() {
            return SystemClock.uptimeMillis() - mAddedUptimeMillis;
        }

        @Override
        public long getTimeoutMillis() {
            return mTimeoutMillis;
        }

        @Override
        public long getTimeoutRemainingMillis() {
            return mTimeoutMillis - getAgeMillis();
        }
    }

    public interface ExpiringIterableLongSparseArrayListener<V> {
        /**
         * @param key   key
         * @param index index
         * @param item  item
         */
        void onItemAdded(long key, int index, @NonNull ItemWrapper<V> item);

        /**
         * @param key   key
         * @param index index
         * @param item  item
         */
        void onItemUpdated(long key, int index, @NonNull ItemWrapper<V> item);

        /**
         * Notify that an item is expiring and give the option to cancel removing the item.
         *
         * @param key   key
         * @param index index
         * @param item  item
         * @return true to forcibly reset the item timeout, false to allow the item to be removed
         */
        boolean onItemExpiring(long key, int index, @NonNull ItemWrapper<V> item);

        /**
         * @param key   key
         * @param index index
         * @param item  item
         */
        void onItemRemoved(long key, int index, @NonNull ItemWrapper<V> item);
    }

    private final String mName;
    private final Object mSyncLock;
    private final ListenerManager<ExpiringIterableLongSparseArrayListener<V>> mListeners;
    private final Handler mHandlerMain;
    private final Handler mHandlerBackground;
    private final IterableLongSparseArray<ItemWrapperImpl<V>> mMapItems;

    private long mDefaultTimeoutMillis;

    private boolean mIsStarted;

    public ExpiringIterableLongSparseArray(String name) {
        this(name, new Object());
    }

    public ExpiringIterableLongSparseArray(String name, int defaultTimeoutMillis) {
        this(name, null, defaultTimeoutMillis);
    }

    public ExpiringIterableLongSparseArray(String name, Looper looper) {
        this(name, null, looper);
    }

    public ExpiringIterableLongSparseArray(String name, int defaultTimeoutMillis, Looper looper) {
        this(name, null, defaultTimeoutMillis, looper);
    }

    public ExpiringIterableLongSparseArray(String name, Object syncLock) {
        this(name, syncLock, null);
    }

    public ExpiringIterableLongSparseArray(String name, Object syncLock, int defaultTimeoutMillis) {
        this(name, syncLock, defaultTimeoutMillis, null);
    }

    public ExpiringIterableLongSparseArray(String name, Object syncLock, Looper looper) {
        this(name, syncLock, DEFAULT_EXPIRATION_TIMEOUT_MILLIS, looper);
    }

    public ExpiringIterableLongSparseArray(String name, Object syncLock, int defaultTimeoutMillis, Looper looper) {
        if (Utils.Companion.isNullOrEmpty(name)) {
            throw new IllegalArgumentException("name must not be null or empty");
        }

        if (syncLock == null) {
            syncLock = new Object();
        }

        if (looper == null) {
            looper = Looper.getMainLooper();
        }

        mName = name;
        mSyncLock = syncLock;
        mListeners = new ListenerManager<>(name + ".mListeners");

        mHandlerMain = new Handler(looper, new Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                return ExpiringIterableLongSparseArray.this.handleMessage(msg);
            }
        });

        HandlerThread handlerThreadBackground = new HandlerThread(
                "\"" + name + "\".mHandlerBackground");
        handlerThreadBackground.start();
        Looper looperBackground = handlerThreadBackground.getLooper();
        mHandlerBackground = new Handler(looperBackground, new Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                return ExpiringIterableLongSparseArray.this.handleMessage(msg);
            }
        });

        mMapItems = new IterableLongSparseArray<>();

        mDefaultTimeoutMillis = defaultTimeoutMillis;
    }

    public Object getSyncLock() {
        return mSyncLock;
    }

    private static abstract class Messages {
        /**
         * <ul>
         * <li>msg.arg1: ?</li>
         * <li>msg.arg2: ?</li>
         * <li>msg.obj: Long key</li>
         * </li>
         * </ul>
         */
        private static final int ExpireItem = 1;
    }

    @SuppressWarnings("UnusedReturnValue")
    private static Message obtainAndSendMessage(Handler handler, int what, Object obj) {
        return obtainAndSendMessage(handler, what, 0, 0, obj);
    }

    @SuppressWarnings("SameParameterValue")
    private static Message obtainAndSendMessage(Handler handler, int what, int arg1, int arg2, Object obj) {
        Message message = handler.obtainMessage(what, arg1, arg2, obj);
        handler.sendMessage(message);
        return message;
    }

    @SuppressWarnings({"UnusedReturnValue", "SameParameterValue"})
    private static Message obtainAndSendMessageDelayed(Handler handler, int what, Object obj, long delayMillis) {
        return obtainAndSendMessageDelayed(handler, what, 0, 0, obj, delayMillis);
    }

    @SuppressWarnings("SameParameterValue")
    private static Message obtainAndSendMessageDelayed(Handler handler, int what, int arg1, int arg2, Object obj, long delayMillis) {
        Message message = handler.obtainMessage(what, arg1, arg2, obj);
        handler.sendMessageDelayed(message, delayMillis);
        return message;
    }

    private boolean handleMessage(Message msg) {
        // We [currently] only have one message, ExpireItem; no need to switch on msg.what

        Long key = (Long) msg.obj;

        if (msg.getTarget() == mHandlerMain) {
            itemExpire(key);
        } else {
            obtainAndSendMessage(mHandlerMain, msg.what, key);
        }

        return false;
    }

    /**
     * Should only be called from inside of a synchronized (mSyncLock) block
     *
     * @param callerName  callerName
     * @param itemWrapper itemWrapper
     */
    private void itemExpirationStop(String callerName, ItemWrapper<V> itemWrapper) {
        Object obj = itemWrapper.getKey();
        if (VERBOSE_LOG_EXPIRE) {
            Log.v(TAG, '#' + mName + ' ' + callerName +
                    "->itemExpirationStop: mHandler.removeMessages(Messages.ExpireKey, obj=" + obj + ')');
        }
        mHandlerBackground.removeMessages(Messages.ExpireItem, obj);
    }

    /**
     * Should only be called from inside of a synchronized (mSyncLock) block
     *
     * @param callerName  callerName
     * @param itemWrapper itemWrapper
     */
    private void itemExpirationStart(String callerName, ItemWrapper<V> itemWrapper) {
        itemExpirationStop(callerName + "->itemExpirationStart", itemWrapper);

        long ageMillis = itemWrapper.getAgeMillis();
        long timeoutRemainingMillis = itemWrapper.getTimeoutRemainingMillis();
        if (timeoutRemainingMillis > 0) {
            Long obj = itemWrapper.getKey();
            if (VERBOSE_LOG_EXPIRE) {
                Log.v(TAG, '#' + mName + ' ' + callerName +
                        "->itemExpirationStart: mHandler.obtainAndSendMessageDelayed(Messages.ExpireKey, obj=" +
                        obj + ", delayMillis=" + timeoutRemainingMillis + ')');
            }
            obtainAndSendMessageDelayed(mHandlerBackground, Messages.ExpireItem, obj, timeoutRemainingMillis);
        }
    }

    private void itemExpire(long key) {
        synchronized (mSyncLock) {
            int index = indexOfKey(key); // binarySearch
            if (index < 0) {
                if (VERBOSE_LOG_EXPIRE) {
                    Log.w(TAG, '#' + mName +
                            " itemExpire: indexOfKey(" + key + ") returned index=" + index +
                            "; item does not exist or has already been removed");
                }
                return;
            }

            ItemWrapper<V> itemWrapper = mMapItems.valueAt(index); // direct

            V value = itemWrapper.getValue();
            long ageMillis = itemWrapper.getAgeMillis();
            long timeoutMillis = itemWrapper.getTimeoutMillis();

            if (VERBOSE_LOG_EXPIRE) {
                Log.w(TAG, '#' + mName +
                        " itemExpire: EXPIRING after " + timeoutMillis + "ms : key=" + key +
                        ", index=" + index + ", value=" + value);
            }

            boolean reset = false;

            synchronized (mListeners) {
                for (ExpiringIterableLongSparseArrayListener<V> listener : mListeners.beginTraversing()) {
                    if (listener.onItemExpiring(key, index, itemWrapper)) {
                        reset = true;
                        break;
                    }
                }
                mListeners.endTraversing();

                if (reset) {
                    if (VERBOSE_LOG_EXPIRE) {
                        Log.w(TAG, '#' + mName +
                                " itemExpire: item expiration reset by listener callback; resetting");
                    }
                    itemExpirationStart("itemExpire", itemWrapper);
                    return;
                }

                Log.w(TAG, '#' + mName +
                        " itemExpire: EXPIRED after " + timeoutMillis + "ms : key=" + key +
                        ", index=" + index + ", value=" + value + "; removing item");

                removeAt(index, true); // direct
            }
        }
    }

    /**
     * Should only be called from inside of a synchronized (mSyncLock) block
     */
    private void itemExpirationsClearAll() {
        Log.v(TAG, '#' + mName + " itemExpirationsClearAll: mHandler.removeCallbacksAndMessages(null)");
        mHandlerBackground.removeCallbacksAndMessages(null);
    }

    public long getDefaultTimeoutMillis() {
        return mDefaultTimeoutMillis;
    }

    /**
     * NOTE: Setting this value resets all item expiration timers
     *
     * @param defaultTimeoutMillis &lt;= 0 to disable
     */
    public void setDefaultTimeoutMillis(long defaultTimeoutMillis) {
        synchronized (mSyncLock) {
            pause();
            mDefaultTimeoutMillis = defaultTimeoutMillis;
            resume();
        }
    }

    /**
     * @return true if already started, false if newly started
     */
    private boolean start(String callerName) {
        if (VERBOSE_LOG_START) {
            Log.v(TAG, '#' + mName + " +start(" + callerName + ')');
        }

        boolean wasStarted;

        synchronized (mSyncLock) {
            wasStarted = mIsStarted;

            if (!mIsStarted) {
                mIsStarted = true;

                ItemWrapper<V> itemWrapper;
                for (int i = 0; i < size(); i++) {
                    itemWrapper = mMapItems.valueAt(i); // direct
                    itemExpirationStart(callerName + "->start", itemWrapper);
                }
            }
        }

        if (VERBOSE_LOG_START) {
            Log.v(TAG, '#' + mName + " -start(" + callerName + ')');
        }

        return wasStarted;
    }

    /**
     * Clears all expiration timers in this collection, effectively pausing it.
     * <p>
     * {@link #resume()} or the next {@link #put(long, Object)} or {@link #setValueAt(int, Object)} will resume the timers.
     */
    public void pause() {
        synchronized (mSyncLock) {
            if (mIsStarted) {
                itemExpirationsClearAll();
            }
        }
    }

    public void resume() {
        synchronized (mSyncLock) {
            if (mIsStarted) {
                ItemWrapper<V> itemWrapper;
                for (int i = 0; i < size(); i++) {
                    itemWrapper = mMapItems.valueAt(i); // direct
                    itemExpirationStart("resume", itemWrapper);
                }
            }
        }
    }

    public void stop() {
        if (VERBOSE_LOG_STOP) {
            Log.v(TAG, '#' + mName + " +stop()");
        }
        synchronized (mSyncLock) {
            pause();
            mIsStarted = false;
        }
        if (VERBOSE_LOG_STOP) {
            Log.v(TAG, '#' + mName + " -stop()");
        }
    }

    public void addListener(ExpiringIterableLongSparseArrayListener<V> listener) {
        synchronized (mListeners) {
            mListeners.attach(listener);
        }
    }

    public void removeListener(ExpiringIterableLongSparseArrayListener<V> listener) {
        synchronized (mListeners) {
            mListeners.detach(listener);
        }
    }

    /**
     * Should only be called from inside of a synchronized (mSyncLock) block
     *
     * @param callerName  callerName
     * @param itemWrapper itemWrapper
     * @param index       index
     * @param expired     expired
     */
    private void onItemRemoved(@SuppressWarnings("SameParameterValue") String callerName, ItemWrapper<V> itemWrapper, int index, boolean expired) {
        if (VERBOSE_LOG_REMOVE) {
            Log.i(TAG, '#' + mName + " +onItemRemoved(" + callerName + ", itemWrapper=" + itemWrapper +
                    ", index=" + index + ", expired=" + expired + ')');
        }

        itemExpirationStop(callerName + "->onItemRemoved", itemWrapper);

        if (mIsStarted && size() == 0) {
            if (VERBOSE_LOG_REMOVE) {
                Log.i(TAG, '#' + mName + ' ' + callerName + "->onItemRemoved: mIsStarted && size() == 0; stop();");
            }
            stop();
        }

        long key = itemWrapper.getKey();
        V value = itemWrapper.getValue();
        long ageMillis = itemWrapper.getAgeMillis();
        long timeoutMillis = itemWrapper.getTimeoutMillis();

        synchronized (mListeners) {
            for (ExpiringIterableLongSparseArrayListener<V> listener : mListeners.beginTraversing()) {
                listener.onItemRemoved(key, index, itemWrapper);
            }
            mListeners.endTraversing();
        }

        if (VERBOSE_LOG_REMOVE) {
            Log.i(TAG, '#' + mName + " -onItemRemoved(" + callerName + ", itemWrapper=" + itemWrapper +
                    ", index=" + index + ", expired=" + expired + ')');
        }
    }

    /**
     * Should only be called from inside of a synchronized (mSyncLock) block
     *
     * @param callerName  callerName
     * @param itemWrapper itemWrapper
     */
    private void onItemWritten(String callerName, int index, ItemWrapper<V> itemWrapper) {
        if (VERBOSE_LOG_UPDATE) {
            Log.i(TAG, '#' + mName + " +onItemWritten(" + callerName + ", index=" + index + ", itemWrapper=" + itemWrapper + ')');
        }
        if (start(callerName + "->onItemWritten")) {
            itemExpirationStart(callerName + "->onItemWritten", itemWrapper);
        }
        if (index < 0) {
            int indexInserted = ~index;
            synchronized (mListeners) {
                for (ExpiringIterableLongSparseArrayListener<V> listener : mListeners.beginTraversing()) {
                    listener.onItemAdded(itemWrapper.getKey(), indexInserted, itemWrapper);
                }
                mListeners.endTraversing();
            }
        } else {
            long ageMillis = itemWrapper.getAgeMillis();
            synchronized (mListeners) {
                for (ExpiringIterableLongSparseArrayListener<V> listener : mListeners.beginTraversing()) {
                    listener.onItemUpdated(itemWrapper.getKey(), index, itemWrapper);
                }
                mListeners.endTraversing();
            }
        }
        if (VERBOSE_LOG_UPDATE) {
            Log.i(TAG, '#' + mName + " -onItemWritten(" + callerName + ", index=" + index + ", itemWrapper=" + itemWrapper + ')');
        }
    }

    public V get(long key) {
        return get(key, null);
    }

    public V get(long key, V valueIfKeyNotFound) {
        V value = valueIfKeyNotFound;
        synchronized (mSyncLock) {
            ItemWrapper<V> itemWrapper = mMapItems.get(key, null); // binarySearch
            if (itemWrapper != null) {
                value = itemWrapper.getValue();
            }
        }
        return value;
    }

    public void delete(long key) {
        synchronized (mSyncLock) {
            if (VERBOSE_LOG_REMOVE) {
                Log.i(TAG, '#' + mName + " delete(key=" + key + ')');
            }
            remove(key);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public V remove(long key) {
        synchronized (mSyncLock) {
            if (VERBOSE_LOG_REMOVE) {
                Log.i(TAG, '#' + mName + " remove(key=" + key + ')');
            }
            return removeAt(indexOfKey(key)); // NOTE: indexOfKey is a binarySearch
        }
    }

    public V removeAt(int index) {
        return removeAt(index, false);
    }

    private V removeAt(int index, boolean expired) {
        if (index < 0) {
            return null;
        }

        V value;
        synchronized (mSyncLock) {
            if (VERBOSE_LOG_REMOVE) {
                Log.i(TAG, '#' + mName + " removeAt(index=" + index + ')');
            }
            ItemWrapper<V> itemWrapper = mMapItems.removeAt(index);
            value = itemWrapper.getValue();
            onItemRemoved("removeAt", itemWrapper, index, expired);
        }
        return value;
    }

    public int put(long key, V value) {
        return put(key, value, mDefaultTimeoutMillis);
    }

    public int put(long key, V value, long timeoutMillis) {
        int index;
        synchronized (mSyncLock) {
            if (VERBOSE_LOG_PUT) {
                Log.i(TAG, '#' + mName +
                        " put(key=" + key + ", value=" + value +
                        ", timeoutMillis=" + timeoutMillis + ')');
            }

            ItemWrapperImpl<V> itemWrapper = mMapItems.get(key);
            if (itemWrapper == null) {
                itemWrapper = new ItemWrapperImpl<>(key, value, timeoutMillis);
            } else {
                itemWrapper.update(value, timeoutMillis);
            }

            index = mMapItems.put(key, itemWrapper); // binarySearch

            onItemWritten("put", index, itemWrapper);
        }
        return index;
    }

    public int size() {
        synchronized (mSyncLock) {
            return mMapItems.size();
        }
    }

    public long keyAt(int index) {
        synchronized (mSyncLock) {
            return mMapItems.keyAt(index); // direct
        }
    }

    public V valueAt(int index) {
        synchronized (mSyncLock) {
            return mMapItems.valueAt(index).getValue(); // direct
        }
    }

    public void setValueAt(int index, V value) {
        setValueAt(index, value, mDefaultTimeoutMillis);
    }

    public void setValueAt(int index, V value, long timeoutMillis) {
        synchronized (mSyncLock) {
            if (VERBOSE_LOG_UPDATE) {
                Log.i(TAG, '#' + mName +
                        " setValueAt(index=" + index + ", value=" + value +
                        ", timeoutMillis=" + timeoutMillis + ')');
            }
            ItemWrapperImpl<V> itemWrapper = mMapItems.valueAt(index); // direct
            itemWrapper.update(value, timeoutMillis);
            onItemWritten("setValueAt", index, itemWrapper);
        }
    }

    public int indexOfKey(long key) {
        synchronized (mSyncLock) {
            return mMapItems.indexOfKey(key); // binarySearch
        }
    }

    public int indexOfValue(V value) {
        synchronized (mSyncLock) {
            ItemWrapper<V> itemWrapper;
            for (int i = 0; i < size(); i++) {
                itemWrapper = mMapItems.valueAt(i); // direct
                if (itemWrapper.getValue().equals(value)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public void clear() {
        synchronized (mSyncLock) {
            if (VERBOSE_LOG_CLEAR) {
                Log.i(TAG, '#' + mName + " clear()");
            }

            while (size() > 0) {
                removeAt(0); // direct
            }
        }
    }

    public Iterator<Long> iterateKeys() {
        synchronized (mSyncLock) {
            return mMapItems.iterateKeys();
        }
    }

    public Iterator<V> iterateValues() {
        synchronized (mSyncLock) {
            return new ExpiringIterableLongSparseArrayValuesIterator<>(this);
        }
    }

    private static final class ExpiringIterableLongSparseArrayValuesIterator<V>
            implements Iterator<V> {
        private final ExpiringIterableLongSparseArray<V> mArray;

        private int mIndex;
        private boolean mCanRemove;

        private ExpiringIterableLongSparseArrayValuesIterator(ExpiringIterableLongSparseArray<V> array) {
            mArray = array;
        }

        @Override
        public boolean hasNext() {
            //
            // NOTE:(pv) mArray.size() causes mArray.gc() to be called
            //
            return mIndex < mArray.size();
        }

        @Override
        public V next() {
            //if (mArray.mName != null)
            //{
            //    Log.e(TAG, '#' + mArray.mName + " next(): " + mArray);
            //}
            //
            // NOTE:(pv) hasNext() causes mArray.gc() to be called
            //
            if (hasNext()) {
                mCanRemove = true;
                //
                // NOTE:(pv) mArray.valueAt(...) causes mArray.gc() to be called
                //
                return mArray.valueAt(mIndex++);
            } else {
                throw new NoSuchElementException("No more elements");
            }
        }

        @Override
        public void remove() {
            //if (mArray.mDebugName != null)
            //{
            //    Log.e(TAG, '#' + mArray.mDebugName + " remove(): BEFORE " + mArray);
            //}
            if (mCanRemove) {
                mCanRemove = false;
                mArray.removeAt(--mIndex);
            } else {
                throw new IllegalStateException("next() must be called");
            }
            //if (mArray.mDebugName != null)
            //{
            //    Log.e(TAG, '#' + mArray.mDebugName + " remove():  AFTER " + mArray);
            //}
        }
    }

    // TODO:(pv) Add back the "pinned" concept?

    /*
    public boolean reset(long key)
    {
        boolean reset;

        synchronized (mSyncLock)
        {
            reset = indexOfKey(key) >= 0; // binarySearch
            if (reset)
            {
                itemExpirationStart("reset", key);
            }
        }

        return reset;
    }
    */
}
