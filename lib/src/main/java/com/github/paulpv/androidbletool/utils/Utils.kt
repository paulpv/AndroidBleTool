package com.github.paulpv.androidbletool.utils

import android.content.Intent
import android.os.Bundle
import android.util.SparseArray
import java.io.UnsupportedEncodingException
import java.util.*
import java.util.concurrent.TimeUnit

object Utils {
    @JvmStatic
    fun TAG(o: Any?): String {
        return TAG(o?.javaClass)
    }

    @JvmStatic
    fun TAG(c: Class<*>?): String {
        return TAG(ReflectionUtils.getShortClassName(c))
    }

    /**
     * Per http://developer.android.com/reference/android/util/Log.html#isLoggable(java.lang.String, int)
     */
    const val LOG_TAG_LENGTH_LIMIT = 23

    /**
     * Limits the tag length to [#LOG_TAG_LENGTH_LIMIT]
     * Example: "ReallyLongClassName" to "ReallyLo…lassName"
     *
     * @param tag
     * @return the tag limited to [#LOG_TAG_LENGTH_LIMIT]
     */
    @JvmStatic
    fun TAG(tag: String): String {
        if (tag.length <= LOG_TAG_LENGTH_LIMIT) {
            return tag
        }
        var length = tag.length

        @Suppress("NAME_SHADOWING")
        val tag = tag.substring(tag.lastIndexOf("$") + 1, length)
        if (tag.length <= LOG_TAG_LENGTH_LIMIT) {
            return tag
        }
        length = tag.length
        val half = LOG_TAG_LENGTH_LIMIT / 2
        return tag.substring(0, half) + '…' + tag.substring(length - half)
    }

    //
    //
    //

    @JvmStatic
    val EMPTY = byteArrayOf(0)

    @JvmStatic
    fun getBytes(value: String): ByteArray {
        return try {
            value.toByteArray(Charsets.UTF_8)
        } catch (e: UnsupportedEncodingException) {
            throw IllegalStateException("UnsupportedEncodingException: Should never happen as long as Utf8Encoding is valid", e)
        }
    }

    @JvmStatic
    fun getString(bytes: ByteArray?, offset: Int, length: Int): String? {
        return try {
            // TODO:(pv) Does this work for *all* UTF8 strings?
            String(bytes!!, offset, length, Charsets.UTF_8)
        } catch (e: UnsupportedEncodingException) {
            // Should *NEVER* happen since this method always uses a supported encoding
            null
        }
    }

    @JvmStatic
    fun isNullOrEmpty(value: String?): Boolean {
        return value == null || value == ""
    }

    /**
     * Identical to [.repr], but grammatically intended for Strings.
     *
     * @param value value
     * @return "null", or '\"' + value.toString + '\"', or value.toString()
     */
    @JvmStatic
    fun quote(value: Any?): String {
        return repr(value, false)
    }

    /**
     * Identical to [.quote], but grammatically intended for Objects.
     *
     * @param value value
     * @return "null", or '\"' + value.toString + '\"', or value.toString()
     */
    @JvmStatic
    fun repr(value: Any?): String {
        return repr(value, false)
    }

    /**
     * @param value    value
     * @param typeOnly typeOnly
     * @return "null", or '\"' + value.toString + '\"', or value.toString(), or getShortClassName(value)
     */
    @JvmStatic
    fun repr(value: Any?, typeOnly: Boolean): String {
        if (value == null) {
            return "null"
        }

        if (value is String) {
            return '\"'.toString() + value.toString() + '\"'.toString()
        }

        if (typeOnly) {
            return ReflectionUtils.getShortClassName(value)
        }

        return if (value is Array<*>) {
            toString(value)
        } else value.toString()
    }

    @JvmStatic
    fun toString(items: Array<*>?): String {
        val sb = StringBuilder()

        if (items == null) {
            sb.append("null")
        } else {
            sb.append('[')
            for (i in items.indices) {
                if (i != 0) {
                    sb.append(", ")
                }
                val item = items[i]
                sb.append(repr(item))
            }
            sb.append(']')
        }

        return sb.toString()
    }

    /**
     * Returns a string composed from a [Map].
     */
    @JvmStatic
    fun <K, V> toString(map: Map<K, V>?): String {
        if (map == null) {
            return "null"
        }
        if (map.isEmpty()) {
            return "{}"
        }

        val buffer = StringBuilder()
        buffer.append('{')
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()

            buffer.append(repr(entry.key)).append("=").append(repr(entry.value))

            if (it.hasNext()) {
                buffer.append(", ")
            }
        }
        buffer.append('}')
        return buffer.toString()
    }

    @JvmStatic
    fun toString(intent: Intent?): String {
        if (intent == null) {
            return "null"
        }

        val sb = StringBuilder()

        sb.append(intent.toString())

        val bundle = intent.extras
        sb.append(", extras=").append(toString(bundle))

        return sb.toString()
    }

    @JvmStatic
    fun toString(bundle: Bundle?): String {
        if (bundle == null) {
            return "null"
        }

        val sb = StringBuilder()

        val keys = bundle.keySet()
        val it = keys.iterator()

        sb.append('{')
        while (it.hasNext()) {
            val key = it.next()
            var value = bundle.get(key)

            sb.append(quote(key)).append('=')

            if (key.toLowerCase(Locale.getDefault()).contains("password")) {
                value = "*CENSORED*"
            }

            when (value) {
                is Bundle -> sb.append(toString(value))
                is Intent -> sb.append(toString(value))
                else -> sb.append(quote(value))
            }

            if (it.hasNext()) {
                sb.append(", ")
            }
        }
        sb.append('}')

        return sb.toString()
    }

    /**
     * Returns a string composed from a [SparseArray].
     */
    @JvmStatic
    fun toString(array: SparseArray<ByteArray>?): String? {
        if (array == null) {
            return "null"
        }
        val buffer = StringBuilder()
        buffer.append('{')
        for (i in 0 until array.size()) {
            buffer.append(array.keyAt(i)).append("=").append(Arrays.toString(array.valueAt(i)))
        }
        buffer.append('}')
        return buffer.toString()
    }

    //
    //
    //

    @JvmStatic
    fun toHexString(value: Byte, maxBytes: Int): String {
        return toHexString(MyMemoryStream.getBytes(value), 0, maxBytes, false)
    }

    @JvmStatic
    fun toHexString(value: Short, maxBytes: Int): String {
        return toHexString(MyMemoryStream.getBytes(value), 0, maxBytes, false)
    }

    @JvmStatic
    fun toHexString(value: Int, maxBytes: Int): String {
        return toHexString(MyMemoryStream.getBytes(value), 0, maxBytes, false)
    }

    @JvmStatic
    fun toHexString(value: Long, maxBytes: Int): String {
        return toHexString(MyMemoryStream.getBytes(value), 0, maxBytes, false)
    }

    @JvmStatic
    fun toHexString(value: String): String {
        return toHexString(value.toByteArray())
    }

    @JvmStatic
    fun toHexString(bytes: ByteArray?): String {
        return toHexString(bytes, true)
    }

    @JvmStatic
    fun toHexString(bytes: ByteArray?, asByteArray: Boolean): String {
        return if (bytes == null) {
            "null"
        } else toHexString(bytes, 0, bytes.size, asByteArray)
    }

    @JvmStatic
    fun toHexString(bytes: ByteArray?, offset: Int, count: Int): String {
        return toHexString(bytes, offset, count, true)
    }

    @JvmStatic
    fun toHexString(bytes: ByteArray?, offset: Int, count: Int, asByteArray: Boolean): String {
        if (bytes == null) {
            return "null"
        }
        val hexChars = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
        )
        val sb = java.lang.StringBuilder()
        if (asByteArray) {
            for (i in offset until count) {
                if (i != offset) {
                    sb.append('-')
                }
                sb.append(hexChars[(0x000000f0 and bytes[i].toInt()) shr 4])
                sb.append(hexChars[(0x0000000f and bytes[i].toInt())])
            }
        } else {
            for (i in count - 1 downTo 0) {
                sb.append(hexChars[(0x000000f0 and bytes[i].toInt()) shr 4])
                sb.append(hexChars[(0x0000000f and bytes[i].toInt())])
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun bytesToHexString(value: Short, maxBytes: Int, lowerCase: Boolean): String {
        return bytesToHexString(value, false, maxBytes, lowerCase)
    }

    @JvmStatic
    fun bytesToHexString(value: Short, reverse: Boolean, maxBytes: Int, lowerCase: Boolean): String {
        @Suppress("NAME_SHADOWING") var value = value
        if (reverse) {
            value = java.lang.Short.reverseBytes(value)
        }
        var s: String = toHexString(value, maxBytes)
        if (lowerCase) {
            s = s.toLowerCase(Locale.ROOT)
        }
        return s
    }

    @JvmStatic
    fun bytesToHexString(value: Int, maxBytes: Int, lowerCase: Boolean): String {
        return bytesToHexString(value, false, maxBytes, lowerCase)
    }

    @JvmStatic
    fun bytesToHexString(value: Int, reverse: Boolean, maxBytes: Int, lowerCase: Boolean): String {
        @Suppress("NAME_SHADOWING") var value = value
        if (reverse) {
            value = java.lang.Integer.reverseBytes(value)
        }
        var s: String = toHexString(value, maxBytes)
        if (lowerCase) {
            s = s.toLowerCase(Locale.ROOT)
        }
        return s
    }

    @JvmStatic
    fun toBitString(value: Byte, maxBits: Int): String? {
        return toBitString(value, maxBits, 8)
    }

    @JvmStatic
    fun toBitString(value: Byte, maxBits: Int, spaceEvery: Int): String? {
        return toBitString(MyMemoryStream.getBytes(value), maxBits, spaceEvery)
    }

    @JvmStatic
    fun toBitString(value: Short, maxBits: Int): String? {
        return toBitString(value, maxBits, 8)
    }

    @JvmStatic
    fun toBitString(value: Short, maxBits: Int, spaceEvery: Int): String? {
        return toBitString(MyMemoryStream.getBytes(value), maxBits, spaceEvery)
    }

    @JvmStatic
    fun toBitString(value: Int, maxBits: Int): String? {
        return toBitString(value, maxBits, 8)
    }

    @JvmStatic
    fun toBitString(value: Int, maxBits: Int, spaceEvery: Int): String? {
        return toBitString(MyMemoryStream.getBytes(value), maxBits, spaceEvery)
    }

    @JvmStatic
    fun toBitString(value: Long, maxBits: Int): String? {
        return toBitString(value, maxBits, 8)
    }

    @JvmStatic
    fun toBitString(value: Long, maxBits: Int, spaceEvery: Int): String? {
        return toBitString(MyMemoryStream.getBytes(value), maxBits, spaceEvery)
    }

    @JvmStatic
    fun toBitString(bytes: ByteArray?, maxBits: Int, spaceEvery: Int): String? {
        val bits = BitSetPlatform(bytes)
        @Suppress("NAME_SHADOWING") val maxBits = 0.coerceAtLeast(maxBits.coerceAtMost(bits.length))
        val sb = java.lang.StringBuilder()
        for (i in maxBits - 1 downTo 0) {
            sb.append(if (bits.get(i)) '1' else '0')
            if (spaceEvery != 0 && i > 0 && i % spaceEvery == 0) {
                sb.append(' ')
            }
        }
        return sb.toString()
    }


    //
    //
    //

    @JvmStatic
    fun split(source: String, separator: String, limit: Int): Array<String> {
        if (isNullOrEmpty(source) || isNullOrEmpty(separator)) {
            return arrayOf(source)
        }

        var indexB = source.indexOf(separator)
        if (indexB == -1) {
            return arrayOf(source)
        }

        var indexA = 0
        var value: String
        val values = ArrayList<String>()

        while (indexB != -1 && (limit < 1 || values.size < limit - 1)) {
            value = source.substring(indexA, indexB)
            if (!isNullOrEmpty(value) || limit < 0) {
                values.add(value)
            }
            indexA = indexB + separator.length
            indexB = source.indexOf(separator, indexA)
        }

        indexB = source.length
        value = source.substring(indexA, indexB)
        if (!isNullOrEmpty(value) || limit < 0) {
            values.add(value)
        }

        return values.toTypedArray()
    }

    //
    //
    //

    @JvmStatic
    fun padNumber(number: Long, ch: Char, minimumLength: Int): String {
        val s = StringBuilder(number.toString())
        while (s.length < minimumLength) {
            s.insert(0, ch)
        }
        return s.toString()
    }

    @JvmStatic
    fun formatNumber(number: Long, minimumLength: Int): String {
        return padNumber(number, '0', minimumLength)
    }

    @JvmStatic
    fun formatNumber(number: Double, leading: Int, trailing: Int): String {
        if (java.lang.Double.isNaN(number) || number == java.lang.Double.NEGATIVE_INFINITY || number == java.lang.Double.POSITIVE_INFINITY) {
            return number.toString()
        }

        // String.valueOf(1) is guaranteed to at least be of the form "1.0"
        val parts = split(number.toString(), ".", 0)
        while (parts[0].length < leading) {
            parts[0] = '0' + parts[0]
        }
        while (parts[1].length < trailing) {
            parts[1] = parts[1] + '0'
        }
        parts[1] = parts[1].substring(0, trailing)
        return parts[0] + '.'.toString() + parts[1]
    }

    /**
     * @param elapsedMillis elapsedMillis
     * @param maximumTimeUnit maximumTimeUnit
     * @return HH:MM:SS.MMM
     */
    @JvmStatic
    fun getTimeDurationFormattedString(elapsedMillis: Long, maximumTimeUnit: TimeUnit? = null): String {
        // TODO:(pv) Get to work for negative values?
        // TODO:(pv) Handle zero value
        @Suppress("NAME_SHADOWING") var elapsedMillis = elapsedMillis
        @Suppress("NAME_SHADOWING") var maximumTimeUnit = maximumTimeUnit
        if (maximumTimeUnit == null) {
            maximumTimeUnit = TimeUnit.HOURS
        }
        if (maximumTimeUnit > TimeUnit.DAYS) {
            throw IllegalArgumentException("maximumTimeUnit must be null or <= TimeUnit.DAYS")
        }
        if (maximumTimeUnit < TimeUnit.MILLISECONDS) {
            throw IllegalArgumentException("maximumTimeUnit must be null or >= TimeUnit.MILLISECONDS")
        }
        val sb = StringBuilder()
        if (maximumTimeUnit >= TimeUnit.DAYS) {
            val days = TimeUnit.MILLISECONDS.toDays(elapsedMillis)
            sb.append(formatNumber(days, 2)).append(':')
            if (days > 0) {
                elapsedMillis -= TimeUnit.DAYS.toMillis(days)
            }
        }
        if (maximumTimeUnit >= TimeUnit.HOURS) {
            val hours = TimeUnit.MILLISECONDS.toHours(elapsedMillis)
            sb.append(formatNumber(hours, 2)).append(':')
            if (hours > 0) {
                elapsedMillis -= TimeUnit.HOURS.toMillis(hours)
            }
        }
        if (maximumTimeUnit >= TimeUnit.MINUTES) {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis)
            sb.append(formatNumber(minutes, 2)).append(':')
            if (minutes > 0) {
                elapsedMillis -= TimeUnit.MINUTES.toMillis(minutes)
            }
        }
        if (maximumTimeUnit >= TimeUnit.SECONDS) {
            val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis)
            sb.append(formatNumber(seconds, 2)).append('.')
            if (seconds > 0) {
                elapsedMillis -= TimeUnit.SECONDS.toMillis(seconds)
            }
        }
        sb.append(formatNumber(elapsedMillis, 3))
        return sb.toString()
    }
}