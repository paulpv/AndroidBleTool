package com.github.paulpv.androidbletool

import kotlin.math.roundToLong

class LowPassFilter {
    companion object {
        @Suppress("MemberVisibilityCanBePrivate", "unused")
        const val ALPHA_LOW = 0.1
        @Suppress("MemberVisibilityCanBePrivate", "unused")
        const val ALPHA_MEDIUM = 0.5
        @Suppress("MemberVisibilityCanBePrivate", "unused")
        const val ALPHA_HIGH = 0.8
        @Suppress("MemberVisibilityCanBePrivate", "unused")
        const val DEFAULT_ALPHA = ALPHA_MEDIUM

        /**
         * @param alpha close to 0 to allow more noise, close to 1 to allow less noise; 0 or 1 to disable
         */
        fun update(value: Long, valuePrevious: Long, alpha: Double = ALPHA_MEDIUM): Long {
            return (value + alpha * (valuePrevious - value)).roundToLong()
        }

        /**
         * @param alpha close to 0 to allow more noise, close to 1 to allow less noise; 0 or 1 to disable
         */
        fun update(value: Double, valuePrevious: Double, alpha: Double = ALPHA_MEDIUM): Double {
            return value + alpha * (valuePrevious - value)
        }
    }
}
