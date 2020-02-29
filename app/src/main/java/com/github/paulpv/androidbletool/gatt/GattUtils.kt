package com.github.paulpv.androidbletool.gatt

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.util.Log
import com.github.paulpv.androidbletool.BluetoothUtils
import com.github.paulpv.androidbletool.utils.Utils.TAG
import com.github.paulpv.androidbletool.utils.Utils.quote
import java.util.*

class GattUtils private constructor() {
    companion object {
        private val TAG = TAG(GattUtils::class.java)

        /**
         * @param callerName
         * @param gatt
         * @return true if both gatt.disconnect() and gatt.close() were called successfully, otherwise false
         */
        @Suppress("unused")
        fun safeDisconnectAndClose(callerName: String, gatt: BluetoothGatt?): Boolean {
            return safeDisconnect(callerName, gatt, true) && safeClose(callerName, gatt)
        }

        /**
         * Oh oh! According to Android bug:
         * https://code.google.com/p/android/issues/detail?id=183108
         *
         *
         * Starting in 5.0, if you call disconnect and then immediately call close the
         *
         * @param callerName
         * @param gatt
         * @return true if gatt.disconnect() was called successfully, otherwise false
         */
        @Suppress("unused")
        fun safeDisconnect(callerName: String, gatt: BluetoothGatt?): Boolean {
            return safeDisconnect(callerName, gatt, false)
        }

        private fun safeDisconnect(callerName: String, gatt: BluetoothGatt?, ignoreException: Boolean): Boolean {
            val debugInfo = "${BluetoothUtils.gattDeviceAddressToString(gatt)} ${quote(callerName)}->safeDisconnect"
            Log.v(TAG, "$debugInfo(gatt=$gatt)")
            if (gatt == null) {
                Log.w(TAG, "$debugInfo: gatt == null; ignoring")
                return false
            }
            try {
                Log.v(TAG, "$debugInfo: +gatt.disconnect()")
                // NOTE:(pv) Android may sometimes still **internally** throw an Exception, ex: DeadObjectException, show it in the log, swallow it, and then move on...
                gatt.disconnect()
                Log.v(TAG, "$debugInfo: -gatt.disconnect()")
            } catch (e: Exception) {
                Log.w(TAG, "$debugInfo: -gatt.disconnect(); EXCEPTION; ignoring", e)
                if (!ignoreException) {
                    return false
                }
            }
            return true
        }

        /**
         * @param callerName
         * @param gatt
         * @return true if gatt.close() was called successfully, otherwise false
         */
        fun safeClose(callerName: String, gatt: BluetoothGatt?): Boolean {
            val debugInfo = "${BluetoothUtils.gattDeviceAddressToString(gatt)} $callerName->safeClose"
            Log.v(TAG, "$debugInfo(gatt=$gatt)")
            if (gatt == null) {
                Log.w(TAG, "$debugInfo: gatt == null; ignoring")
                return false
            }
            //
            // Similar to suggestion per
            // 1) https://issuetracker.google.com/issues/37057260#comment10
            //  and
            // 2) https://github.com/NordicSemiconductor/Android-BLE-Library/blob/master/ble/src/main/java/no/nordicsemi/android/ble/BleManager.java#L782
            //
            try {
                Thread.sleep(200)
            } catch (e: InterruptedException) {
                // ignore
            }
            try {
                Log.v(TAG, "$debugInfo: +gatt.close()")
                gatt.close()
                Log.v(TAG, "$debugInfo: -gatt.close()")
            } catch (e: Exception) {
                Log.w(TAG, "$debugInfo: -gatt.close() EXCEPTION; ignoring", e)
                return false
            }
            return true
        }

        @Suppress("unused")
        fun createBluetoothGattCharacteristic(serviceUuid: UUID?, characteristicUuid: UUID?): BluetoothGattCharacteristic {
            val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val characteristic = BluetoothGattCharacteristic(characteristicUuid, 0, 0)
            service.addCharacteristic(characteristic)
            return characteristic
        }

        //
        //
        //

        /**
         * Code taken from [BluetoothGattCharacteristic.setValue]
         *
         * @param value      New value for this characteristic
         * @param formatType Integer format type used to transform the value parameter
         * @param offset     Offset at which the value should be placed
         * @return
         */
        @Suppress("unused")
        fun toBytes(value: Int, formatType: Int, offset: Int): ByteArray {
            @Suppress("NAME_SHADOWING") var value = value
            @Suppress("NAME_SHADOWING") var offset = offset
            val bytes = ByteArray(offset + getTypeLen(formatType))
            when (formatType) {
                BluetoothGattCharacteristic.FORMAT_SINT8 -> {
                    value = intToSignedBits(value, 8)
                    bytes[offset] = (value and 0xFF).toByte()
                }
                BluetoothGattCharacteristic.FORMAT_UINT8 -> bytes[offset] = (value and 0xFF).toByte()
                BluetoothGattCharacteristic.FORMAT_SINT16 -> {
                    value = intToSignedBits(value, 16)
                    bytes[offset++] = (value and 0xFF).toByte()
                    bytes[offset] = ((value shr 8) and 0xFF).toByte()
                }
                BluetoothGattCharacteristic.FORMAT_UINT16 -> {
                    bytes[offset++] = (value and 0xFF).toByte()
                    bytes[offset] = ((value shr 8) and 0xFF).toByte()
                }
                BluetoothGattCharacteristic.FORMAT_SINT32 -> {
                    value = intToSignedBits(value, 32)
                    bytes[offset++] = (value and 0xFF).toByte()
                    bytes[offset++] = ((value shr 8) and 0xFF).toByte()
                    bytes[offset++] = ((value shr 16) and 0xFF).toByte()
                    bytes[offset] = ((value shr 24) and 0xFF).toByte()
                }
                BluetoothGattCharacteristic.FORMAT_UINT32 -> {
                    bytes[offset++] = (value and 0xFF).toByte()
                    bytes[offset++] = ((value shr 8) and 0xFF).toByte()
                    bytes[offset++] = ((value shr 16) and 0xFF).toByte()
                    bytes[offset] = ((value shr 24) and 0xFF).toByte()
                }
                else -> throw NumberFormatException("Unknown formatType $formatType")
            }
            return bytes
        }

        /**
         * Code taken from [BluetoothGattCharacteristic.setValue]
         *
         * @param mantissa   Mantissa for this characteristic
         * @param exponent   exponent value for this characteristic
         * @param formatType Float format type used to transform the value parameter
         * @param offset     Offset at which the value should be placed
         * @return
         */
        @Suppress("unused")
        fun toBytes(mantissa: Int, exponent: Int, formatType: Int, offset: Int): ByteArray {
            @Suppress("NAME_SHADOWING") var mantissa = mantissa
            @Suppress("NAME_SHADOWING") var exponent = exponent
            @Suppress("NAME_SHADOWING") var offset = offset
            val bytes = ByteArray(offset + getTypeLen(formatType))
            when (formatType) {
                BluetoothGattCharacteristic.FORMAT_SFLOAT -> {
                    mantissa = intToSignedBits(mantissa, 12)
                    exponent = intToSignedBits(exponent, 4)
                    bytes[offset++] = (mantissa and 0xFF).toByte()
                    bytes[offset] = ((mantissa shr 8) and 0x0F).toByte()
                    bytes[offset] = (bytes[offset] + ((exponent and 0x0F) shl 4)).toByte()
                }
                BluetoothGattCharacteristic.FORMAT_FLOAT -> {
                    mantissa = intToSignedBits(mantissa, 24)
                    exponent = intToSignedBits(exponent, 8)
                    bytes[offset++] = (mantissa and 0xFF).toByte()
                    bytes[offset++] = ((mantissa shr 8) and 0xFF).toByte()
                    bytes[offset++] = ((mantissa shr 16) and 0xFF).toByte()
                    bytes[offset] = (bytes[offset] + (exponent and 0xFF)).toByte()
                }
                else -> throw NumberFormatException("Unknown formatType $formatType")
            }
            return bytes
        }

        /**
         * Code taken from [BluetoothGattCharacteristic.setValue]
         *
         * @param value New value for this characteristic
         * @return true if the locally stored value has been set
         */
        @Suppress("unused")
        fun toBytes(value: String?): ByteArray {
            return value?.toByteArray() ?: byteArrayOf()
        }

        /**
         * Code taken from [BluetoothGattCharacteristic.getTypeLen]
         * Returns the size of a give value type.
         */
        @Suppress("KDocUnresolvedReference")
        private fun getTypeLen(formatType: Int): Int {
            return formatType and 0xF
        }

        /**
         * Code taken from [BluetoothGattCharacteristic.intToSignedBits]
         * Convert an integer into the signed bits of a given length.
         */
        @Suppress("KDocUnresolvedReference")
        private fun intToSignedBits(i: Int, size: Int): Int {
            @Suppress("NAME_SHADOWING") var i = i
            if (i < 0) {
                i = (1 shl size - 1) + (i and (1 shl size - 1) - 1)
            }
            return i
        }
    }
}