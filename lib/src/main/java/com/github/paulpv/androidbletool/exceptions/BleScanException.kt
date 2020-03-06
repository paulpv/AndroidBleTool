package com.github.paulpv.androidbletool.exceptions

import android.bluetooth.le.ScanCallback
import androidx.annotation.IntDef
import java.util.*

class BleScanException : BleException {
    companion object {
        /**
         * From hidden @see ScanCallback#NO_ERROR
         */
        const val NO_ERROR = 0

        /**
         * Fails to start scan as BLE scan with the same settings is already started by the app. Only on API >=21.
         */
        const val SCAN_FAILED_ALREADY_STARTED = ScanCallback.SCAN_FAILED_ALREADY_STARTED

        /**
         * Fails to start scan as app cannot be registered. Only on API >=21.
         */
        const val SCAN_FAILED_APPLICATION_REGISTRATION_FAILED = ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED

        /**
         * Fails to start scan due an internal error. Only on API >=21.
         */
        const val SCAN_FAILED_INTERNAL_ERROR = ScanCallback.SCAN_FAILED_INTERNAL_ERROR

        /**
         * Fails to start power optimized scan as this feature is not supported. Only on API >=21.
         */
        const val SCAN_FAILED_FEATURE_UNSUPPORTED = ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED

        /**
         * Fails to start scan as it is out of hardware resources. Only on API >=21.
         * From hidden @see ScanCallback#SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES
         */
        const val SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES = 5

        /**
         * On API >=25 there is an undocumented scan throttling mechanism. If 5 scans were started by the app during a 30 second window
         * the next scan in that window will be silently skipped with only a log warning. In this situation there should be
         * a retryDateSuggestion [Date] set with a time when the scan should work again.
         *
         * @link https://blog.classycode.com/undocumented-android-7-ble-behavior-changes-d1a9bd87d983
         *
         * From hidden @see ScanCallback#SCAN_FAILED_SCANNING_TOO_FREQUENTLY
         */
        const val SCAN_FAILED_SCANNING_TOO_FREQUENTLY = 6

        /**
         * Scan did not start because Bluetooth cannot start.
         */
        const val BLUETOOTH_CANNOT_START = 100

        /**
         * Scan did not start correctly because the Bluetooth adapter was disabled.
         * Ask the user to turn on Bluetooth or use **android.bluetooth.adapter.action.REQUEST_ENABLE**
         */
        const val BLUETOOTH_DISABLED = 101

        /**
         * Scan did not start correctly because the device does not support it.
         */
        const val BLUETOOTH_NOT_AVAILABLE = 102

        /**
         * Scan did not start correctly because the user did not accept access to location services. On Android 6.0 and up you must ask the
         * user about **ACCESS_COARSE_LOCATION** in runtime.
         */
        const val LOCATION_PERMISSION_MISSING = 103

        /**
         * Scan did not start because location services are disabled on the device. On Android 6.0 and up location services must be enabled
         * in order to receive BLE scan results.
         */
        const val LOCATION_SERVICES_DISABLED = 104

        //
        //
        //

        private fun createMessage(errorCode: Int, retryDateSuggestion: Date?): String {
            return "${errorCodeToString(errorCode)} ${retryDateSuggestionIfExists(retryDateSuggestion)}"
        }

        private fun errorCodeToString(errorCode: Int): String {
            val message = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "SCAN_FAILED_ALREADY_STARTED"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED"
                SCAN_FAILED_INTERNAL_ERROR -> "SCAN_FAILED_INTERNAL_ERROR"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "SCAN_FAILED_FEATURE_UNSUPPORTED"
                SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES"
                SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "SCAN_FAILED_SCANNING_TOO_FREQUENTLY"
                BLUETOOTH_CANNOT_START -> "BLUETOOTH_CANNOT_START"
                BLUETOOTH_DISABLED -> "BLUETOOTH_DISABLED"
                BLUETOOTH_NOT_AVAILABLE -> "BLUETOOTH_NOT_AVAILABLE"
                LOCATION_PERMISSION_MISSING -> "LOCATION_PERMISSION_MISSING"
                LOCATION_SERVICES_DISABLED -> "LOCATION_SERVICES_DISABLED"
                else -> "SCAN_FAILED_UNKNOWN"
            }
            return "$message($errorCode)"
        }

        private fun retryDateSuggestionIfExists(retryDateSuggestion: Date?): String {
            return if (retryDateSuggestion == null) {
                ""
            } else {
                ", suggested retry date is $retryDateSuggestion"
            }
        }
    }


    @IntDef(
        SCAN_FAILED_ALREADY_STARTED,
        SCAN_FAILED_APPLICATION_REGISTRATION_FAILED,
        SCAN_FAILED_INTERNAL_ERROR,
        SCAN_FAILED_FEATURE_UNSUPPORTED,
        SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES,
        SCAN_FAILED_SCANNING_TOO_FREQUENTLY,
        BLUETOOTH_CANNOT_START,
        BLUETOOTH_DISABLED,
        BLUETOOTH_NOT_AVAILABLE,
        LOCATION_PERMISSION_MISSING,
        LOCATION_SERVICES_DISABLED
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class ErrorCode

    /**
     * Returns the reason code of scan failure.
     *
     * @return One of the [ErrorCode] codes.
     */
    @get:ErrorCode
    @ErrorCode
    val errorCode: Int

    /**
     * Returns a [Date] suggestion when a particular [Reason] should no longer be valid
     *
     * @return the date suggestion or null if no suggestion available
     */
    @Suppress("MemberVisibilityCanBePrivate")
    val retryDateSuggestion: Date?

    @Suppress("unused")
    constructor(@ErrorCode errorCode: Int) : super(createMessage(errorCode, null)) {
        this.errorCode = errorCode
        retryDateSuggestion = null
    }

    @Suppress("unused")
    constructor(@ErrorCode errorCode: Int, retryDateSuggestion: Date) : super(createMessage(errorCode, retryDateSuggestion)) {
        this.errorCode = errorCode
        this.retryDateSuggestion = retryDateSuggestion
    }

    @Suppress("unused")
    constructor(@ErrorCode errorCode: Int, causeException: Throwable?) : super(createMessage(errorCode, null), causeException) {
        this.errorCode = errorCode
        retryDateSuggestion = null
    }
}