package com.github.paulpv.androidbletool.gatt

import android.content.Context
import android.os.Looper
import android.util.Log
import com.github.paulpv.androidbletool.BluetoothUtils.Companion.throwExceptionIfInvalidBluetoothAddress
import com.github.paulpv.androidbletool.collections.IterableLongSparseArray
import com.github.paulpv.androidbletool.utils.Utils.Companion.TAG

class GattManager constructor(val context: Context, looper: Looper? = null) {
    companion object {
        private val TAG = TAG(GattManager::class.java)
    }

    val looper: Looper?

    private val gattHandlers = IterableLongSparseArray<GattHandler>()

    init {
        @Suppress("NAME_SHADOWING") var looper = looper
        if (looper == null) {
            looper = Looper.getMainLooper()
        }
        this.looper = looper
    }

    /**
     * Allocates a GattHandler. To free the GattHandler, call [GattHandler.close]
     *
     * @param deviceAddress deviceAddress
     * @return never null
     */
    fun getGattHandler(deviceAddress: Long): GattHandler {
        throwExceptionIfInvalidBluetoothAddress(deviceAddress)
        synchronized(gattHandlers) {
            var gattHandler = gattHandlers[deviceAddress]
            if (gattHandler == null) {
                gattHandler = GattHandler(this, deviceAddress)
                gattHandlers.put(deviceAddress, gattHandler)
            }
            return gattHandler
        }
    }

    fun removeGattHandler(gattHandler: GattHandler) {
        gattHandlers.remove(gattHandler.deviceAddressLong)
    }

    fun close() {
        Log.v(TAG, "+close()")
        synchronized(gattHandlers) {
            val it = gattHandlers.iterateValues()
            while (it.hasNext()) {
                val gattHandler = it.next()
                it.remove()
                gattHandler.close(false)
            }
        }
        Log.v(TAG, "-close()")
    }
}