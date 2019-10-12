package com.github.paulpv.androidbletool

import java.util.*

@Suppress("MemberVisibilityCanBePrivate")
class Utils {
    companion object {

        @Suppress("FunctionName")
        fun TAG(o: Any?): String {
            return TAG(o?.javaClass)
        }

        @Suppress("FunctionName")
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
        @Suppress("FunctionName")
        fun TAG(tag: String): String {
            if (tag.length <= LOG_TAG_LENGTH_LIMIT) {
                return tag
            }
            var length = tag.length
            var _tag = tag.substring(tag.lastIndexOf("$") + 1, length)
            if (_tag.length <= LOG_TAG_LENGTH_LIMIT) {
                return _tag
            }
            length = _tag.length
            val half = LOG_TAG_LENGTH_LIMIT / 2
            return _tag.substring(0, half) + '…' + _tag.substring(length - half)
        }

        fun isNullOrEmpty(value: String?): Boolean {
            return value == null || value == ""
        }

        /**
         * Identical to [.repr], but grammatically intended for Strings.
         *
         * @param value value
         * @return "null", or '\"' + value.toString + '\"', or value.toString()
         */
        fun quote(value: Any?): String {
            return repr(value, false)
        }

        /**
         * Identical to [.quote], but grammatically intended for Objects.
         *
         * @param value value
         * @return "null", or '\"' + value.toString + '\"', or value.toString()
         */
        fun repr(value: Any?): String {
            return repr(value, false)
        }

        /**
         * @param value    value
         * @param typeOnly typeOnly
         * @return "null", or '\"' + value.toString + '\"', or value.toString(), or getShortClassName(value)
         */
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

        fun padNumber(number: Long, ch: Char, minimumLength: Int): String {
            val s = StringBuilder(number.toString())
            while (s.length < minimumLength) {
                s.insert(0, ch)
            }
            return s.toString()
        }

        fun formatNumber(number: Long, minimumLength: Int): String {
            return padNumber(number, '0', minimumLength)
        }

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
         * @param msElapsed msElapsed
         * @return HH:MM:SS.MMM
         */
        fun getTimeDurationFormattedString(msElapsed: Long): String {
            @Suppress("NAME_SHADOWING") var msElapsed = msElapsed
            var h: Long = 0
            var m: Long = 0
            var s: Long = 0
            if (msElapsed > 0) {
                h = (msElapsed / (3600 * 1000)).toInt().toLong()
                msElapsed -= h * 3600 * 1000
                m = (msElapsed / (60 * 1000)).toInt().toLong()
                msElapsed -= m * 60 * 1000
                s = (msElapsed / 1000).toInt().toLong()
                msElapsed -= s * 1000
            } else {
                msElapsed = 0
            }

            return formatNumber(h, 2) + ":" +
                    formatNumber(m, 2) + ":" +
                    formatNumber(s, 2) + "." +
                    formatNumber(msElapsed, 3)
        }
    }
}