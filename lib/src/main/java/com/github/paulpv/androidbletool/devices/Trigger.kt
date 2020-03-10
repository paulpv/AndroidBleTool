package com.github.paulpv.androidbletool.devices

abstract class Trigger<T>(
    @Suppress("MemberVisibilityCanBePrivate") val isImmediate: Boolean,
    val value: T
) {
    companion object {
        fun toString(triggers: Set<Trigger<*>>?): String? {
            if (triggers == null) {
                return "null"
            }
            val sb = StringBuilder()
            sb.append('[')
            val iterator: Iterator<Trigger<*>> = triggers.iterator()
            while (iterator.hasNext()) {
                val trigger: Trigger<*>? = iterator.next()
                sb.append(trigger)
                if (iterator.hasNext()) {
                    sb.append(", ")
                }
            }
            sb.append(']')
            return sb.toString()
        }
    }

    /**
     * Used to determine if the trigger should be removed from notifications of updates.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var isChanged: Boolean = false

    override fun toString(): String {
        return "${javaClass.simpleName}@${Integer.toHexString(hashCode())}{value=$value, isImmediate=$isImmediate, isChanged=$isChanged}"
    }

    class TriggerAdvertisementSpeed(value: Byte) : Trigger<Byte>(true, value)

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