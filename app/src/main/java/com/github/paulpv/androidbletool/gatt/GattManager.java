package com.github.paulpv.androidbletool.gatt;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import com.github.paulpv.androidbletool.BluetoothUtils;
import com.github.paulpv.androidbletool.collections.IterableLongSparseArray;
import com.github.paulpv.androidbletool.utils.Utils;

import java.util.Iterator;

public class GattManager {
    private static final String TAG = Utils.Companion.TAG(GattManager.class);

    private final Context mContext;
    private final Looper mLooper;
    private final IterableLongSparseArray<GattHandler> mGattHandlers;

    @SuppressWarnings("unused")
    public GattManager(Context context) {
        this(context, null);
    }

    public GattManager(Context context, Looper looper) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        if (looper == null) {
            looper = Looper.getMainLooper();
        }

        mContext = context;
        mLooper = looper;

        mGattHandlers = new IterableLongSparseArray<>();
    }

    public Context getContext() {
        return mContext;
    }

    //package
    Looper getLooper() {
        return mLooper;
    }

    /**
     * Allocates a GattHandler. To free the GattHandler, call {@link GattHandler#close()}
     *
     * @param deviceAddress deviceAddress
     * @return never null
     */
    public GattHandler getGattHandler(long deviceAddress) {
        BluetoothUtils.Companion.throwExceptionIfInvalidBluetoothAddress(deviceAddress);
        synchronized (mGattHandlers) {
            GattHandler gattHandler = mGattHandlers.get(deviceAddress);
            if (gattHandler == null) {
                gattHandler = new GattHandler(this, deviceAddress);
                mGattHandlers.put(deviceAddress, gattHandler);
            }
            return gattHandler;
        }
    }

    //package
    void removeGattHandler(GattHandler gattHandler) {
        if (gattHandler == null) {
            throw new IllegalArgumentException("gattHandler must not be null");
        }
        long deviceAddress = gattHandler.getDeviceAddressLong();
        mGattHandlers.remove(deviceAddress);
    }

    @SuppressWarnings("unused")
    public void close() {
        Log.v(TAG, "+close()");
        synchronized (mGattHandlers) {
            Iterator<GattHandler> it = mGattHandlers.iterateValues();
            while (it.hasNext()) {
                GattHandler gattHandler = it.next();

                it.remove();

                gattHandler.close(false);
            }
        }
        Log.v(TAG, "-close()");
    }
}
