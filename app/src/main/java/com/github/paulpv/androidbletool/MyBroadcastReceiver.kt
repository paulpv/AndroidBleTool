package com.github.paulpv.androidbletool

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

abstract class MyBroadcastReceiver(TAG: String, private val context: Context) : BroadcastReceiver() {

    @Suppress("PrivatePropertyName")
    private val TAG = Utils.TAG(TAG)

    abstract val intentFilter: IntentFilter

    private var isRegistered = false

    fun register() {
        Log.i(TAG, "+register()")
        if (!isRegistered) {
            Log.v(TAG, "register: context.registerReceiver(this, intentFilter)")
            context.registerReceiver(this, intentFilter)

            isRegistered = true
        }
        Log.i(TAG, "-register()")
    }

    fun unregister() {
        Log.i(TAG, "+unregister()")
        if (isRegistered) {
            Log.v(TAG, "register: context.unregisterReceiver(this)")
            context.unregisterReceiver(this)
        }
        Log.i(TAG, "-unregister()")
    }

    override fun onReceive(context: Context, intent: Intent) {
        for (action in intentFilter.actionsIterator()) {
            if (action == intent.action) {
                BleTool.getInstance(context)?.onBroadcastReceived(this, context, intent)
                break
            }
        }
    }
}