package com.github.paulpv.testapp

import android.app.Activity
import android.util.Log
import androidx.annotation.StringRes
import com.github.paulpv.androidbletool.R
import com.github.paulpv.androidbletool.exceptions.BleScanException
import com.google.android.material.snackbar.Snackbar
import java.util.*
import java.util.concurrent.TimeUnit

internal fun Activity.showSnackbarShort(text: CharSequence) {
    Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_SHORT).show()
}

internal fun Activity.showSnackbarShort(@StringRes text: Int) {
    Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_SHORT).show()
}

//
//
//

private val ERROR_MESSAGES = mapOf(
    BleScanException.SCAN_FAILED_ALREADY_STARTED to R.string.error_scan_failed_already_started,
    BleScanException.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED to R.string.error_scan_failed_application_registration_failed,
    BleScanException.SCAN_FAILED_INTERNAL_ERROR to R.string.error_scan_failed_internal_error,
    BleScanException.SCAN_FAILED_FEATURE_UNSUPPORTED to R.string.error_scan_failed_feature_unsupported,
    BleScanException.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES to R.string.error_scan_failed_out_of_hardware_resources,
    BleScanException.SCAN_FAILED_SCANNING_TOO_FREQUENTLY to R.string.error_undocumented_scan_throttle,
    BleScanException.BLUETOOTH_CANNOT_START to R.string.error_bluetooth_cannot_start,
    BleScanException.BLUETOOTH_DISABLED to R.string.error_bluetooth_disabled,
    BleScanException.BLUETOOTH_NOT_AVAILABLE to R.string.error_bluetooth_not_available,
    BleScanException.LOCATION_PERMISSION_MISSING to R.string.error_location_permission_missing,
    BleScanException.LOCATION_SERVICES_DISABLED to R.string.error_location_services_disabled
)

internal fun Activity.showError(exception: BleScanException) =
    getErrorMessage(exception).let { errorMessage ->
        Log.e("Scanning", errorMessage, exception)
        showSnackbarShort(errorMessage)
    }

private fun Activity.getErrorMessage(exception: BleScanException): String =
    // Special case, as there might or might not be a retry date suggestion
    if (exception.errorCode == BleScanException.SCAN_FAILED_SCANNING_TOO_FREQUENTLY) {
        getScanThrottleErrorMessage(exception.retryDateSuggestion)
    } else {
        // Handle all other possible errors
        ERROR_MESSAGES[exception.errorCode]?.let { errorResId ->
            getString(errorResId)
        } ?: run {
            // unknown error - return default message
            Log.w("Scanning", String.format(getString(R.string.error_no_message), exception.errorCode))
            getString(R.string.error_unknown_error)
        }
    }

private fun Activity.getScanThrottleErrorMessage(retryDate: Date?): String =
    with(StringBuilder(getString(R.string.error_undocumented_scan_throttle))) {
        retryDate?.let { date ->
            String.format(
                Locale.getDefault(),
                getString(R.string.error_undocumented_scan_throttle_retry),
                date.secondsUntil
            ).let { append(it) }
        }
        toString()
    }

private val Date.secondsUntil: Long
    get() = TimeUnit.MILLISECONDS.toSeconds(time - System.currentTimeMillis())