package com.github.paulpv.androidbletool.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.github.paulpv.androidbletool.R
import com.github.paulpv.androidbletool.utils.Utils.TAG

object ActivityUtils {
    private val TAG = TAG(ActivityUtils.javaClass)

    fun resolveActivity(context: Context, intent: Intent): ComponentName? {
        return intent.resolveActivity(context.getPackageManager())
    }

    fun startActivityForResult(activity: Activity, intent: Intent, requestCode: Int, options: Bundle?): ComponentName? {
        var componentName: ComponentName? = resolveActivity(activity, intent)
        if (componentName != null) {
            try {
                try {
                    ActivityCompat.startActivityForResult(activity, intent, requestCode, options)
                } catch (e: IllegalArgumentException) {
                    //
                    // Grrr!
                    // https://www.google.com/search?q=android+makeSceneTransitionAnimation+startactivity+IllegalArgumentException
                    //
                    Log.e(TAG, "startActivityForResult: IllegalArgumentException")
                    ActivityCompat.startActivityForResult(activity, intent, requestCode, null)
                }
            } catch (e: ActivityNotFoundException) {
                componentName = null
            }
        }
        return componentName
    }

    fun startActivityForResult(fragment: Fragment, intent: Intent, requestCode: Int, options: Bundle?): ComponentName? {
        var componentName: ComponentName? = null
        val activity: Activity? = fragment.activity
        if (activity != null) {
            componentName = resolveActivity(activity, intent)
            if (componentName != null) {
                try {
                    try {
                        fragment.startActivityForResult(intent, requestCode, options)
                    } catch (e: IllegalArgumentException) {
                        //
                        // Grrr!
                        // https://www.google.com/search?q=android+makeSceneTransitionAnimation+startactivity+IllegalArgumentException
                        //
                        Log.e(TAG, "startActivityForResult: IllegalArgumentException")
                        fragment.startActivityForResult(intent, requestCode, null)
                    }
                } catch (e: ActivityNotFoundException) {
                    componentName = null
                }
            }
        }
        return componentName
    }

    fun getSelectRingtoneIntent(context: Context, selectedRingtone: Uri?): Intent {
        val title: String = context.getString(R.string.select_ringtone)
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false) // Hide "Default"
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false) // Hide "None"
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
        if (selectedRingtone != null) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selectedRingtone)
        }
        return intent
    }

    fun startSelectRingtoneActivityForResult(activity: Activity, requestCode: Int, selectedRingtone: Uri?): Boolean {
        val intent = getSelectRingtoneIntent(activity, selectedRingtone)
        return startActivityForResult(activity, intent, requestCode, null) != null
    }

    fun getSelectRingtoneActivityResult(data: Intent?): Uri? {
        return data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    }

}