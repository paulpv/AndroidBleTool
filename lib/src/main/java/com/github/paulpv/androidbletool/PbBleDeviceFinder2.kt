package com.github.paulpv.androidbletool

import android.util.Log
import androidx.core.util.size
import com.github.paulpv.androidbletool.collections.ExpiringIterableLongSparseArray
import com.github.paulpv.androidbletool.gatt.GattHandler
import com.github.paulpv.androidbletool.gatt.GattUuids
import com.github.paulpv.androidbletool.logging.MyLog
import com.github.paulpv.androidbletool.utils.Utils.TAG
import java.nio.ByteBuffer

object PbBleDeviceFinder2 {
    private val TAG = TAG(PbBleDeviceFinder2::class.java)

    private val PLAY_JINGLE_COUNT_1 = byteArrayOf(0x01, 0x00)
    private val PLAY_JINGLE_COUNT_2 = byteArrayOf(0x01, 0x00, 0x00)
    private val PLAY_JINGLE_COUNT_3 = byteArrayOf(0x01, 0x00, 0x00, 0x00)
    private val PLAY_JINGLE_COUNT_4 = byteArrayOf(0x80.toByte(), 0x01)

    @JvmStatic
    fun requestBeep(bleDevice: BleDevice) {
        val gattHandler = bleDevice.gattHandler

        val runDisconnect = Runnable {
            Log.e(TAG, "DISCONNECTING")
            gattHandler.disconnect(runAfterDisconnect = Runnable {
                Log.e(TAG, "DISCONNECTED!")
            })
        }
        val runBeep = Runnable {
            val service = GattUuids.PEBBLEBEE_FINDER_SERVICE.uuid
            val characteristic = GattUuids.PEBBLEBEE_FINDER_CHARACTERISTIC1.uuid
            val value = PLAY_JINGLE_COUNT_4
            Log.e(TAG, "WRITING")
            if (!gattHandler.characteristicWrite(
                    serviceUuid = service,
                    characteristicUuid = characteristic,
                    value = value,
                    characteristicWriteType = GattHandler.CharacteristicWriteType.DefaultWithResponse,
                    runAfterSuccess = Runnable {
                        Log.e(TAG, "WRITE SUCCESS!")
                        runDisconnect.run()
                    },
                    runAfterFail = Runnable {
                        Log.e(TAG, "WRITE FAIL!")
                        runDisconnect.run()
                    }
                )
            ) {
                runDisconnect.run()
            }
        }

        if (gattHandler.isConnectingOrConnectedAndNotDisconnecting) {
            runBeep.run()
        } else {
            Log.e(TAG, "CONNECTING")
            if (!gattHandler.connect(runAfterConnect = Runnable {
                    Log.e(TAG, "CONNECT SUCCESS!")
                    runBeep.run()
                }, runAfterFail = Runnable {
                    Log.e(TAG, "CONNECT FAIL!")
                    runDisconnect.run()
                })) {
                runDisconnect.run()
            }
        }
    }

    fun parseScan(item: ExpiringIterableLongSparseArray.ItemWrapper<BleScanResult>) {
        val bleScanResult = item.value
        val scanResult = bleScanResult.scanResult
        val device = scanResult.device

        val deviceMacAddress: String = device.address

        val debugInfo = "$deviceMacAddress parse"

        val scanRecord = scanResult.scanRecord ?: return

        val manufacturerSpecificData = scanRecord.manufacturerSpecificData

        for (i in 0 until manufacturerSpecificData.size) {
            val manufacturerId = manufacturerSpecificData.keyAt(i)
            val manufacturerSpecificDataBytes = manufacturerSpecificData.get(manufacturerId)
            val manufacturerSpecificDataByteBuffer = ByteBuffer.wrap(manufacturerSpecificDataBytes)
            manufacturerSpecificDataByteBuffer.rewind()

            //...

            val position = manufacturerSpecificDataByteBuffer.position()
            val length = manufacturerSpecificDataByteBuffer.limit()
            val remaining = length - position
            if (remaining > 0) {
                Log.w(TAG, "$debugInfo: manufacturerSpecificData $remaining unprocessed bytes")
                MyLog.logManufacturerSpecificData(
                    MyLog.MyLogLevel.Warn, TAG, debugInfo, manufacturerSpecificData
                )
            }
        }
    }
}