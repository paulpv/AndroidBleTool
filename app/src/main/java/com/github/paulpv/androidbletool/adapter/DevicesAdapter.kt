package com.github.paulpv.androidbletool.adapter

import android.content.Context
import android.view.ViewGroup
import com.github.paulpv.androidbletool.BluetoothUtils
import com.github.paulpv.androidbletool.ExpiringIterableLongSparseArray
import com.github.paulpv.androidbletool.R
import com.polidea.rxandroidble2.scan.ScanResult
import java.util.*

class DevicesAdapter(context: Context) :
    SortableAdapter<SortBy, ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>, DevicesViewHolder>(context, SortBy.SignalLevelRssi) {

    override fun getComparator(sortBy: SortBy?, reversed: Boolean): Comparator<ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>> {
        val comparator: Comparator<ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>> = when (sortBy) {
            SortBy.Address -> SORT_BY_ADDRESS
            SortBy.Name -> SORT_BY_NAME
            SortBy.SignalLevelRssi -> SORT_BY_STRENGTH
            SortBy.Age -> SORT_BY_AGE
            SortBy.TimeoutRemaining -> SORT_BY_TIMEOUT_REMAINING
            else -> throw IllegalStateException("unhandled sortBy=$sortBy")
        }
        return if (reversed) Collections.reverseOrder(comparator) else comparator
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DevicesViewHolder {
        val viewGroup = inflate(R.layout.device_cell, parent) as ViewGroup
        return DevicesViewHolder(viewGroup)
    }

    companion object {
        private val SORT_BY_ADDRESS = Comparator<ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>> { obj1, obj2 ->
            BluetoothUtils.macAddressStringToLong(obj1.value.bleDevice.macAddress)
                .compareTo(BluetoothUtils.macAddressStringToLong(obj2.value.bleDevice.macAddress))
        }

        private val SORT_BY_NAME = Comparator<ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>> { obj1, obj2 ->
            (obj1.value.bleDevice.name ?: "").compareTo(obj2.value.bleDevice.name ?: "")
        }

        private val SORT_BY_STRENGTH = Comparator<ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>> { obj1, obj2 ->
            //
            // NOTE: Intentionally inverted to initially sort RSSIs **descending**.
            //
            obj2.value.rssi.compareTo(obj1.value.rssi)
        }

        private val SORT_BY_AGE = Comparator<ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>> { obj1, obj2 ->
            obj1.ageMillis.compareTo(obj2.ageMillis)
        }

        private val SORT_BY_TIMEOUT_REMAINING = Comparator<ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>> { obj1, obj2 ->
            obj1.timeoutRemainingMillis.compareTo(obj2.timeoutRemainingMillis)
        }
    }
}
