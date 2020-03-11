package com.github.paulpv.androidbletool.devices

import com.github.paulpv.androidbletool.utils.ReflectionUtils.instanceName

object Triggers {
    open class Trigger<T>(
        @Suppress("MemberVisibilityCanBePrivate") val isImmediate: Boolean,
        val value: T
    ) {
        override fun toString(): String {
            return "${instanceName(this)}{value=$value, isImmediate=$isImmediate, isChanged=$isChanged}"
        }

        var isChanged = false

        @Suppress("MemberVisibilityCanBePrivate")
        fun setIsChanged(isChanged: Boolean): Boolean {
            this.isChanged = isChanged
            return getIsChanged()
        }

        fun getIsChanged(): Boolean {
            return isChanged
        }
    }

    class TriggerAdvertisementSpeed(value: Byte) : Trigger<Byte>(true, value) {
        object AdvertisementSpeed {
            const val FAST: Byte = 0
            const val SLOW: Byte = 1
            fun toString(value: Byte): String {
                val s: String = when (value) {
                    FAST -> "FAST"
                    SLOW -> "SLOW"
                    else -> "UNKNOWN"
                }
                return "$s($value)"
            }
        }

        override fun toString(): String {
            return "${instanceName(this)}{value=${AdvertisementSpeed.toString(value)}, isImmediate=$isImmediate, isChanged=$isChanged}"
        }
    }

    class TriggerBeepingAndFlashing(isBeepingAndFlashing: Boolean) : Trigger<Boolean>(true, isBeepingAndFlashing)

    class TriggerShortClick(isShortClicked: Boolean, val sequence: Byte = -1, val counter: Byte = -1) : Trigger<Boolean>(true, isShortClicked)

    class TriggerLongClick(isLongClicked: Boolean, val sequence: Byte = -1, val counter: Byte = -1) : Trigger<Boolean>(true, isLongClicked)

    class TriggerDoubleClick(isDoubleClick: Boolean, val sequence: Byte = -1, val counter: Byte = -1) : Trigger<Boolean>(true, isDoubleClick)

    class TriggerTemperatureCelsius(celsius: Short) : Trigger<Short>(false, celsius)

    class TriggerBatteryLevelMilliVolts(milliVolts: Short) : Trigger<Short>(false, milliVolts)

    class TriggerMotion(value: Boolean) : Trigger<Boolean>(true, value)

    class TriggerContinuousScan(durationMillis: Int = INTERRUPT_CONTINUOUS_SCAN_DURATION_MILLIS) : Trigger<Int>(true, durationMillis) {
        companion object {
            const val INTERRUPT_CONTINUOUS_SCAN_DURATION_MILLIS = 2000
        }
    }

    class TriggerSignalLevelRssi(rssi: Int) : Trigger<Int>(false, rssi)

    class TriggerModelNumber(modelNumber: Int) : Trigger<Int>(false, modelNumber)
}