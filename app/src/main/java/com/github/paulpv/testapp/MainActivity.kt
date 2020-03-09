package com.github.paulpv.testapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.app.NavUtils
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.paulpv.androidbletool.BleScanResult
import com.github.paulpv.androidbletool.BleTool
import com.github.paulpv.androidbletool.BleTool.BleToolObserver
import com.github.paulpv.androidbletool.BleTool.DeviceScanObserver
import com.github.paulpv.androidbletool.devices.PbBleDeviceFinder2
import com.github.paulpv.androidbletool.R
import com.github.paulpv.androidbletool.collections.ExpiringIterableLongSparseArray
import com.github.paulpv.androidbletool.exceptions.BleScanException
import com.github.paulpv.androidbletool.utils.Utils
import com.github.paulpv.androidbletool.utils.Utils.TAG
import com.github.paulpv.testapp.adapter.DeviceInfo
import com.github.paulpv.testapp.adapter.DevicesAdapter
import com.github.paulpv.testapp.adapter.SortBy
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), DeviceScanObserver, BleToolObserver {
    companion object {
        private val TAG = TAG(MainActivity::class.java)
    }

    private var switchScan: SwitchCompat? = null
    private var devicesAdapter: DevicesAdapter? = null

    private var bleTool: BleTool? = null

    private val isPersistentScanningEnabled: Boolean
        get() = bleTool!!.isPersistentScanningEnabled

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        Log.i(TAG, "onCreate: intent=${Utils.toString(intent)}")

        bleTool = BleTool.getInstance(this)

        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
            if (NavUtils.getParentActivityName(this) != null) {
                supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            }
        }

        devicesAdapter = DevicesAdapter(this, SortBy.SignalLevelRssi)
        devicesAdapter!!.setEventListener(object : DevicesAdapter.EventListener<DeviceInfo> {
            override fun onItemSelected(item: DeviceInfo) {
                Log.e(TAG, "onItemSelected: Make $item beep!!!")
                val bleDevice = bleTool!!.getBleDevice(item.macAddress)
                PbBleDeviceFinder2.requestBeep(bleDevice)
            }
        })

        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        val dividerItemDecoration = DividerItemDecoration(this, layoutManager.orientation)
        with(scan_results) {
            setLayoutManager(layoutManager)
            addItemDecoration(dividerItemDecoration)
            setHasFixedSize(true)
            itemAnimator = null
            adapter = devicesAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        devicesAdapter?.onResume(bleTool!!.recentlyNearbyDevicesIterator, isPersistentScanningEnabled)
        if (switchScan != null) {
            switchScan!!.isChecked = isPersistentScanningEnabled
        }
    }

    override fun onPause() {
        super.onPause()
        devicesAdapter?.onPause()
    }

    //
    //region Menu stuff...
    //

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_activity, menu)

        val item = menu.findItem(R.id.action_toggle_scanning)
        if (item != null) {
            val actionView = item.actionView
            if (actionView != null) {
                val textView = actionView.findViewById<TextView>(R.id.action_switch_text)
                if (textView != null) {
                    val title = item.title
                    textView.text = title
                    textView.visibility = View.VISIBLE
                }
                switchScan = actionView.findViewById(R.id.action_switch_control)
                if (switchScan != null) {
                    switchScan!!.isChecked = isPersistentScanningEnabled
                    switchScan!!.setOnCheckedChangeListener { buttonView, isChecked ->
                        if (!bleTool?.persistentScanningEnable(isChecked)!!) {
                            buttonView.isChecked = false
                        }
                        devicesAdapter!!.autoUpdateVisibleItems(isChecked)
                    }
                }
            }
        }

        val checkedMenuItem: MenuItem? = when (devicesAdapter!!.sortBy) {
            SortBy.Address -> menu.findItem(R.id.action_sortby_address)
            SortBy.Name -> menu.findItem(R.id.action_sortby_name)
            SortBy.SignalLevelRssi -> menu.findItem(R.id.action_sortby_signal_level_rssi)
            SortBy.Age -> menu.findItem(R.id.action_sortby_age)
            SortBy.TimeoutRemaining -> menu.findItem(R.id.action_sortby_timeout_remaining)
            else -> null
        }
        if (checkedMenuItem != null) {
            checkedMenuItem.isChecked = true
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        val itemId = item!!.itemId
        if (itemId == R.id.action_sortby_address) {
            devicesAdapter!!.sortBy = SortBy.Address
            item.isChecked = true
            return true
        }
        if (itemId == R.id.action_sortby_name) {
            devicesAdapter!!.sortBy = SortBy.Name
            item.isChecked = true
            return true
        }
        if (itemId == R.id.action_sortby_signal_level_rssi) {
            devicesAdapter!!.sortBy = SortBy.SignalLevelRssi
            item.isChecked = true
            return true
        }
        if (itemId == R.id.action_sortby_age) {
            devicesAdapter!!.sortBy = SortBy.Age
            item.isChecked = true
            return true
        }
        if (itemId == R.id.action_sortby_timeout_remaining) {
            devicesAdapter!!.sortBy = SortBy.TimeoutRemaining
            item.isChecked = true
            return true
        }
        if (itemId == R.id.action_clear) {
            devicesAdapter!!.clear()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    //
    //endregion
    //

    override fun onScanStarted(bleTool: BleTool) {
        Log.i(TAG, "onDeviceScanStarted")
        if (switchScan != null) {
            switchScan!!.isChecked = true
        }
    }

    override fun onScanStopped(bleTool: BleTool, error: Throwable?) {
        Log.i(TAG, "onDeviceScanStopped")
        if (switchScan != null) {
            switchScan!!.isChecked = false
        }
        if (error != null) {
            when (error) {
                is BleScanException -> showError(error)
                else -> showSnackbarShort(error.message ?: "null")
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateScanCount() {
        text_scan_count.text = "(${devicesAdapter!!.itemCount})"
    }

    override fun onDeviceAdded(bleTool: BleTool, item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        Log.w(TAG, "  onDeviceAdded: persistentScanningElapsedMillis=${bleTool.persistentScanningElapsedMillis} item=${item}")
        devicesAdapter!!.add(item)
        updateScanCount()
    }

    override fun onDeviceUpdated(bleTool: BleTool, item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        Log.w(TAG, "onDeviceUpdated: persistentScanningElapsedMillis=${bleTool.persistentScanningElapsedMillis} item=${item}")
        devicesAdapter!!.add(item)
    }

    override fun onDeviceRemoved(bleTool: BleTool, item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        Log.w(TAG, "onDeviceRemoved: persistentScanningElapsedMillis=${bleTool.persistentScanningElapsedMillis} item=${item}")
        devicesAdapter!!.remove(item)
        updateScanCount()
    }
}
