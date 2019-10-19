package com.github.paulpv.androidbletool

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
import com.github.paulpv.androidbletool.adapter.DevicesAdapter
import com.github.paulpv.androidbletool.adapter.SortBy
import com.github.paulpv.androidbletool.adapter.SortableAdapter
import com.polidea.rxandroidble2.exceptions.BleScanException
import com.polidea.rxandroidble2.scan.ScanResult
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity(), BleTool.DeviceScanObserver {
    companion object {
        private val TAG = Utils.TAG(MainActivity::class.java)
    }

    private var switchScan: SwitchCompat? = null
    private var devicesAdapter: DevicesAdapter? = null

    private var bleTool: BleTool? = null

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

        devicesAdapter = DevicesAdapter(this)
        devicesAdapter!!.setEventListener(object : SortableAdapter.EventListener<ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>> {
            override fun onItemSelected(item: ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>) {
                Log.e(TAG, "onItemSelected: TODO:(pv) do something w/ $item!")
            }
        })
        with(scan_results) {
            setHasFixedSize(true)
            itemAnimator = null
            adapter = devicesAdapter
        }
    }

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
                    switchScan!!.isChecked = bleTool!!.isPersistentScanningEnabled
                    switchScan!!.setOnCheckedChangeListener { buttonView, isChecked ->
                        if (!bleTool?.persistentScanningEnable(isChecked)!!) {
                            buttonView.isChecked = false
                        }
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

    override fun onDeviceScanError(bleTool: BleTool, e: Throwable): Boolean {
        Log.e(TAG, "onError: e=$e")
        when (e) {
            is BleScanException -> showError(e)
            else -> showSnackbarShort(e.message ?: "null")
        }
        return false
    }

    override fun onDeviceAdded(bleTool: BleTool, item: ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>) {
        val scanResult = item.value
        Log.e(TAG, "onDeviceAdded: persistentScanningElapsedMillis=${bleTool.persistentScanningElapsedMillis} scanResult=${scanResult}")
        //val device = MyBleDevice(scanResult)
        devicesAdapter!!.put(item)
        text_scan_count.text = String.format(Locale.getDefault(), "(%d)", devicesAdapter!!.itemCount)
    }

    override fun onDeviceUpdated(bleTool: BleTool, item: ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>) {
        val scanResult = item.value
        Log.w(TAG, "onDeviceUpdated: persistentScanningElapsedMillis=${bleTool.persistentScanningElapsedMillis} scanResult=${scanResult}")
        //val device = MyBleDevice(scanResult)
        devicesAdapter!!.put(item)
    }

    override fun onDeviceRemoved(bleTool: BleTool, item: ExpiringIterableLongSparseArray.ItemWrapper<ScanResult>) {
        val scanResult = item.value
        Log.e(TAG, "onDeviceRemoved: persistentScanningElapsedMillis=${bleTool.persistentScanningElapsedMillis} scanResult=${scanResult}")
        //val device = MyBleDevice(scanResult)
        devicesAdapter!!.remove(item, false)
        text_scan_count.text = String.format(Locale.getDefault(), "(%d)", devicesAdapter!!.itemCount)
    }
}
