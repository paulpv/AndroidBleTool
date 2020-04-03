package com.github.paulpv.androidbletool.devices

import com.github.paulpv.androidbletool.devices.pebblebee.Pebblebee.ActionSequence
import com.github.paulpv.androidbletool.utils.ReflectionUtils.instanceName

object Triggers {
    open class Trigger<T>(
        @Suppress("MemberVisibilityCanBePrivate") val isImmediate: Boolean,
        val value: T
    ) {
        companion object {
            fun toString(trigger: Trigger<*>, valueFieldString: String, nonValueFieldsString: String): String {
                return "${instanceName(trigger)}{ value=$valueFieldString, $nonValueFieldsString }"
            }
        }

        protected open fun getValueString(): String {
            return "value=$value"
        }

        protected open fun getFieldsString(): String {
            return "isImmediate=$isImmediate, isChanged=$isChanged"
        }

        final override fun toString(): String {
            return toString(this, getValueString(), getFieldsString())
        }

        var isChanged = false

        /*
        @Suppress("MemberVisibilityCanBePrivate")
        fun setIsChanged(isChanged: Boolean): Boolean {
            this.isChanged = isChanged
            return getIsChanged()
        }

        @Suppress("MemberVisibilityCanBePrivate")
        fun getIsChanged(): Boolean {
            return isChanged
        }
        */
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

        override fun getValueString(): String {
            return AdvertisementSpeed.toString(value)
        }
    }

    class TriggerBeepingAndFlashing(isBeepingAndFlashing: Boolean) : Trigger<Boolean>(true, isBeepingAndFlashing)

    abstract class TriggerClick(isClicked: Boolean, val sequence: Byte = -1, val counter: Byte = -1) : Trigger<Boolean>(true, isClicked) {
        override fun getFieldsString(): String {
            return super.getFieldsString() +
                    ", sequence=${ActionSequence.toString(sequence)}" +
                    ", counter=$counter"
        }
    }

    class TriggerShortClick(isShortClicked: Boolean, sequence: Byte = -1, counter: Byte = -1) : TriggerClick(isShortClicked, sequence, counter)

    class TriggerLongClick(isLongClicked: Boolean, sequence: Byte = -1, counter: Byte = -1) : TriggerClick(isLongClicked, sequence, counter)

    class TriggerDoubleClick(isDoubleClick: Boolean, sequence: Byte = -1, counter: Byte = -1) : TriggerClick(isDoubleClick, sequence, counter)

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