package com.github.paulpv.androidbletool.logging

import android.util.Log
import com.github.paulpv.androidbletool.utils.Utils

object MyLog {
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
        Log.println(level, tag, sb.toString())

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
}