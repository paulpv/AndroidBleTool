package com.github.paulpv.androidbletool

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

/**
 * Exposes a base Service via a Binder so that {@link Service#startForeground} can be called
 */
class NotificationService : Service() {
    override fun onBind(intent: Intent?): IBinder {
        return NotificationBinder()
    }

    inner class NotificationBinder : Binder() {
        fun getServiceInstance(): NotificationService = this@NotificationService
    }
}