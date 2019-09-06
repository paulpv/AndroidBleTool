package com.github.paulpv.androidbletool

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var bleTool: BleTool

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleTool = (applicationContext as BleTool.BleToolApp).bleTool
        setContentView(R.layout.activity_main)
        scan_start_btn.setOnClickListener { bleTool.scan(this) }
        scan_stop_btn.setOnClickListener { bleTool.scan(false) }
    }
}
