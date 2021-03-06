package com.github.paulpv.testapp

import android.annotation.SuppressLint
import android.content.Intent
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
import com.github.paulpv.androidbletool.BleDevice
import com.github.paulpv.androidbletool.BleScanResult
import com.github.paulpv.androidbletool.BleTool
import com.github.paulpv.androidbletool.BleTool.BleToolDeviceScanObserver
import com.github.paulpv.androidbletool.BleTool.BleToolScanObserver
import com.github.paulpv.androidbletool.R
import com.github.paulpv.androidbletool.collections.ExpiringIterableLongSparseArray
import com.github.paulpv.androidbletool.devices.Features
import com.github.paulpv.androidbletool.exceptions.BleScanException
import com.github.paulpv.androidbletool.utils.Utils
import com.github.paulpv.androidbletool.utils.Utils.TAG
import com.github.paulpv.testapp.adapter.DeviceInfo
import com.github.paulpv.testapp.adapter.DevicesAdapter
import com.github.paulpv.testapp.adapter.SortBy
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), BleToolDeviceScanObserver, BleToolScanObserver {
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
        Log.v(TAG, "onCreate: intent=${Utils.toString(intent)}")

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
                val macAddress = item.macAddress
                Log.i(TAG, "onItemSelected: Make $macAddress beep!!!")
                requestBeep(macAddress)
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
        if (itemId == R.id.action_clear) {
            devicesAdapter!!.clear()
            return true
        }
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
        if (itemId == R.id.action_ringtone) {
            bleTool!!.selectRingtone(this, 100)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    //
    //endregion Menu stuff...
    //

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        bleTool!!.onActivityResult(requestCode, resultCode, data)
    }

    //
    //
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
        Log.i(TAG, "  onDeviceAdded: persistentScanningElapsedMillis=${bleTool.persistentScanningElapsedMillis} item=${item}")
        devicesAdapter!!.add(item)
        updateScanCount()
    }

    override fun onDeviceUpdated(bleTool: BleTool, item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        Log.v(TAG, "onDeviceUpdated: persistentScanningElapsedMillis=${bleTool.persistentScanningElapsedMillis} item=${item}")
        devicesAdapter!!.add(item)
    }

    override fun onDeviceRemoved(bleTool: BleTool, item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        Log.i(TAG, "onDeviceRemoved: persistentScanningElapsedMillis=${bleTool.persistentScanningElapsedMillis} item=${item}")
        devicesAdapter!!.remove(item)
        updateScanCount()
    }

    //
    //
    //

    private fun requestBeep(macAddress: String) {
        Log.i(TAG, "requestBeep($macAddress)")
        val bleDevice = bleTool!!.deviceFactory.getDevice(macAddress)
        if (bleDevice !is Features.IFeatureBeep) {
            Log.w(TAG, "requestBeep: $bleDevice does not support IFeatureBeep")
            return
        }
        bleDevice.requestBeep(true, object : BleDevice.RequestProgress {
            override fun onConnecting() {
                Log.i(TAG, "CONNECTING")
            }

            override fun onConnected() {
                Log.i(TAG, "CONNECTED")
            }

            override fun onRequesting() {
                Log.i(TAG, "REQUESTING")
            }

            override fun onRequested(success: Boolean) {
                Log.i(TAG, "REQUESTED success=$success")
            }

            override fun onDisconnecting() {
                Log.i(TAG, "DISCONNECTING")
            }

            override fun onDisconnected(success: Boolean) {
                if (success) {
                    Log.i(TAG, "DISCONNECTED success=$success")
                } else {
                    Log.e(TAG, "DISCONNECTED success=$success")
                }
            }
        })
    }
}
