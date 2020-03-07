package com.github.paulpv.androidbletool.logging

import android.util.Log
import android.util.SparseArray
import com.github.paulpv.androidbletool.BluetoothUtils
import com.github.paulpv.androidbletool.utils.Utils

object MyLog {
    interface MyLogLevel {
        companion object {
            /**
             * "Verbose should never be compiled into an application except during development."
             */
            const val Verbose = 2

            /**
             * "Debug logs are compiled in but stripped at runtime."
             */
            const val Debug = 3

            /**
             * "Error, warning and info logs are always kept."
             */
            const val Info = 4

            /**
             * "Error, warning and info logs are always kept."
             */
            const val Warn = 5

            /**
             * "Error, warning and info logs are always kept."
             */
            const val Error = 6

            /**
             * "Report a condition that should never happen."
             */
            const val Fatal = 7
        }
    }

    /*
    public static final int VERBOSE = Log.VERBOSE;
    public static final int FATAL   = 0;          // Log.ASSERT;
    public static final int ERROR   = Log.ERROR;
    public static final int WARN    = Log.WARN;
    public static final int INFO    = Log.INFO;
    public static final int DEBUG   = Log.DEBUG;
    */
    private val sPbLogToAdbLogLevels = intArrayOf(
        -1,  // 0
        -1,  // 1
        Log.VERBOSE,  // 2 PbLog.LogLevel.Verbose
        Log.DEBUG,  // 3 PbLog.LogLevel.Debug
        Log.INFO,  // 4 PbLog.LogLevel.Info
        Log.WARN,  // 5 PbLog.LogLevel.Warn
        Log.ERROR,  // 6 PbLog.LogLevel.Error
        Log.ASSERT // 7 PbLog.LogLevel.Fatal
    )

    fun println(tag: String, level: Int, msg: String, e: Throwable?) {
        // LogCat does not output the Thread ID; prepend msg with it here.

        // LogCat does not output the Thread ID; prepend msg with it here.
        val sb = java.lang.StringBuilder()
            //.append('T').append(Process.myTid()).append(' ')
            .append(msg)

        // LogCat does not output the exception; append msg with it here.
        if (e != null) {
            sb.append(": throwable=").append(Log.getStackTraceString(e))
        }

        //noinspection WrongConstant
        Log.println(sPbLogToAdbLogLevels[level], tag, sb.toString())

    }

    fun logBytes(tag: String, level: Int, text: String, name: String, bytes: ByteArray?) {
        val bytesString: String
        if (bytes == null) {
            bytesString = "$name=null"
        } else {
            val bytesLength = bytes.size
            val prefix = "$name($bytesLength)=["
            bytesString = prefix + Utils.toHexString(bytes) + ']'
            val padding = StringBuilder()
            run {
                var i = 0
                val prefixLength = prefix.length
                while (i < prefixLength) {
                    padding.append(' ')
                    i++
                }
            }
            val reference100s = if (bytesLength > 100) ByteArray(bytesLength) else null
            val reference10s = if (bytesLength > 10) ByteArray(bytesLength) else null
            val reference1s = ByteArray(bytesLength)
            for (i in 0 until bytesLength) {
                if (reference100s != null) {
                    reference100s[i] = (i / 100).toByte()
                }
                if (reference10s != null) {
                    reference10s[i] = (i / 10).toByte()
                }
                reference1s[i] = (i % 10).toByte()
            }
            if (reference100s != null) {
                println(tag, level, text + ": " + padding + Utils.toHexString(reference100s), null)
            }
            if (reference10s != null) {
                println(tag, level, text + ": " + padding + Utils.toHexString(reference10s), null)
            }
            println(tag, level, text + ": " + padding + Utils.toHexString(reference1s), null)
        }
        println(tag, level, "$text: $bytesString", null)
    }

    fun logManufacturerSpecificData(logLevel: Int, tag: String, debugInfo: String, manufacturerSpecificData: SparseArray<ByteArray>?) {
        if (manufacturerSpecificData != null && manufacturerSpecificData.size() > 0) {
            for (i in 0 until manufacturerSpecificData.size()) {
                val manufacturerId = manufacturerSpecificData.keyAt(i)
                val manufacturerSpecificDataBytes = manufacturerSpecificData.valueAt(i)
                val name = String.format("manufacturerSpecificDataBytes[manufacturerId=0x%04X]", manufacturerId)
                logBytes(tag, logLevel, debugInfo, name, manufacturerSpecificDataBytes)
            }
        } else {
            Log.v(tag, "$debugInfo: manufacturerSpecificData=${Utils.toString(manufacturerSpecificData)}")
        }
    }
}