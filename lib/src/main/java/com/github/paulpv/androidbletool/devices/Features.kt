package com.github.paulpv.androidbletool.devices

import android.util.Log
import com.github.paulpv.androidbletool.BuildConfig
import com.github.paulpv.androidbletool.math.LowPassFilter
import com.github.paulpv.androidbletool.utils.ReflectionUtils.instanceName
import com.github.paulpv.androidbletool.utils.Utils.TAG

object Features {
    interface IFeatureListener

    interface IFeature {
        val device: PebblebeeDevice
    }

    abstract class Feature(override val device: PebblebeeDevice) : IFeature {
        companion object {
            fun toString(feature: Feature, close: Boolean = true): String {
                // device.toString() calls feature.toString() for each feature; prevent recursion...
                val device = feature.device
                val deviceString = "${instanceName(device)}{macAddressString=${device.macAddressString}, ...}"
                val s = "${instanceName(this)}{device=${deviceString}"
                return if (close) "$s, ...}" else s

            }
        }

        override fun toString(): String {
            return toString(this)
        }

        init {
            reset()
        }

        abstract fun reset()
    }

    interface IFeatureSignalLevelRssiListener : IFeatureListener {
        fun onFeatureChanged(feature: IFeatureSignalLevelRssi): Boolean
    }

    interface IFeatureSignalLevelRssi : IFeature {
        fun addListener(listener: IFeatureSignalLevelRssiListener)
        fun removeListener(listener: IFeatureSignalLevelRssiListener)
        val signalLevelRssiRealtime: Int
        val signalLevelRssiSmoothed: Int
    }

    class FeatureSignalLevelRssi(device: PebblebeeDevice) : Feature(device), IFeatureSignalLevelRssi {
        companion object {
            private val TAG = TAG(FeatureSignalLevelRssi::class.java)

            const val SIGNAL_LEVEL_RSSI_UNDEFINED = -1

            fun isUndefined(rssi: Int): Boolean {
                return rssi == SIGNAL_LEVEL_RSSI_UNDEFINED
            }

            private const val VERBOSE_LOG = false
        }

        private val listeners = mutableSetOf<IFeatureSignalLevelRssiListener>()

        private var signalLevelRssiRealtimeCurrent = 0
        private var signalLevelRssiRealtimePrevious = 0
        private var signalLevelRssiSmoothedCurrent = 0
        private var signalLevelRssiSmoothedPrevious = 0

        override fun toString(): String {
            return toString(this, close = false) +
                    ", signalLevelRssiRealtimeCurrent=$signalLevelRssiRealtimeCurrent" +
                    ", signalLevelRssiRealtimePrevious=$signalLevelRssiRealtimePrevious" +
                    ", signalLevelRssiSmoothedCurrent=$signalLevelRssiSmoothedCurrent" +
                    ", signalLevelRssiSmoothedPrevious=$signalLevelRssiSmoothedPrevious}"
        }

        override fun addListener(listener: IFeatureSignalLevelRssiListener) {
            synchronized(listeners) { listeners.add(listener) }
        }

        override fun removeListener(listener: IFeatureSignalLevelRssiListener) {
            synchronized(listeners) { listeners.remove(listener) }
        }

        fun onFeatureChanged() {
            synchronized(listeners) {
                for (listener in listeners) {
                    if (listener.onFeatureChanged(this)) {
                        break
                    }
                }
            }
        }

        override fun reset() {
            setSignalLevelRssi(SIGNAL_LEVEL_RSSI_UNDEFINED)
            signalLevelRssiRealtimePrevious = SIGNAL_LEVEL_RSSI_UNDEFINED
            signalLevelRssiSmoothedPrevious = SIGNAL_LEVEL_RSSI_UNDEFINED
        }

        fun copy(other: FeatureSignalLevelRssi) {
            signalLevelRssiRealtimeCurrent = other.signalLevelRssiRealtimeCurrent
            signalLevelRssiRealtimePrevious = other.signalLevelRssiRealtimePrevious
            signalLevelRssiSmoothedCurrent = other.signalLevelRssiSmoothedCurrent
            signalLevelRssiSmoothedPrevious = other.signalLevelRssiSmoothedPrevious
        }

        fun setSignalLevelRssi(rssi: Int): Boolean {
            val triggerSignalLevelRssi = Triggers.TriggerSignalLevelRssi(rssi)
            return setSignalLevelRssi(triggerSignalLevelRssi)
        }

        fun setSignalLevelRssi(trigger: Triggers.TriggerSignalLevelRssi): Boolean {
            //try
            //{
            //    Log.e(TAG, "+setSignalLevelRssi(rssi=" + rssi + ')');
            var rssi: Int = trigger.value
            signalLevelRssiRealtimePrevious = signalLevelRssiRealtimeCurrent
            @Suppress("ConstantConditionIf")
            if (VERBOSE_LOG) {
                Log.e(TAG, "setSignalLevelRssi: signalLevelRssiRealtimePrevious=$signalLevelRssiRealtimePrevious")
            }
            var changed = signalLevelRssiRealtimePrevious != rssi
            signalLevelRssiRealtimeCurrent = rssi
            if (VERBOSE_LOG) {
                Log.e(TAG, "setSignalLevelRssi: signalLevelRssiRealtimeCurrent=$signalLevelRssiRealtimeCurrent")
            }
            if (rssi != SIGNAL_LEVEL_RSSI_UNDEFINED) {
                if (signalLevelRssiSmoothedCurrent != SIGNAL_LEVEL_RSSI_UNDEFINED) {
                    rssi = LowPassFilter.update(signalLevelRssiSmoothedCurrent.toLong(), rssi.toLong()).toInt()
                }
            }
            signalLevelRssiSmoothedPrevious = signalLevelRssiSmoothedCurrent
            if (FeatureSignalLevelRssi.VERBOSE_LOG) {
                Log.e(TAG, "setSignalLevelRssi: signalLevelRssiSmoothedPrevious=$signalLevelRssiSmoothedPrevious")
            }
            changed = if (false && BuildConfig.DEBUG) {
                // NOTE:(pv) In debug mode we want to see the realtime RSSI values
                changed or (signalLevelRssiSmoothedPrevious != rssi)
            } else {
                signalLevelRssiSmoothedPrevious != rssi
            }
            signalLevelRssiSmoothedCurrent = rssi
            if (VERBOSE_LOG) {
                Log.e(TAG, "setSignalLevelRssi: signalLevelRssiSmoothedCurrent=$signalLevelRssiSmoothedCurrent")
            }
            trigger.setIsChanged(changed)
            if (changed) {
                onFeatureChanged()
            }
            return changed
            //}
            //finally
            //{
            //    Log.e(TAG, "-setSignalLevelRssi(rssi=" + rssi + ')');
            //}
        }

        override val signalLevelRssiRealtime: Int
            get() {
                return signalLevelRssiRealtimeCurrent
            }

        override val signalLevelRssiSmoothed: Int
            get() {
                return signalLevelRssiSmoothedCurrent
            }
    }
}