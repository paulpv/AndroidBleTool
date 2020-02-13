package com.github.paulpv.androidbletool

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

class DeviceActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_MAC_ADDRESS = "EXTRA_MAC_ADDRESS"

        fun newInstance(context: Context, macAddress: String): Intent =
            Intent(context, DeviceActivity::class.java).apply { putExtra(EXTRA_MAC_ADDRESS, macAddress) }
    }

    //...
}