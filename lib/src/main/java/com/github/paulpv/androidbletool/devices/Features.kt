package com.github.paulpv.androidbletool.devices

import android.util.Log
import com.github.paulpv.androidbletool.BleDevice
import com.github.paulpv.androidbletool.BuildConfig
import com.github.paulpv.androidbletool.math.LowPassFilter
import com.github.paulpv.androidbletool.utils.ReflectionUtils.instanceName
import com.github.paulpv.androidbletool.utils.Utils.TAG

object Features {
    interface IFeatureListener

    interface IFeature {
        val device: BleDevice
    }

    abstract class Feature(override val device: BleDevice) : IFeature {
        companion object {
            fun toString(feature: Feature, suffix: String? = null): String {
                // device.toString() calls feature.toString() for each feature; prevent recursion...
                val device = feature.device
                val deviceString = "${instanceName(device)}{ macAddressString=${device.macAddressString}, ... }"
                var s = "${instanceName(feature)}{ device=${deviceString}"
                if (suffix != null) {
                    s += suffix
                }
                return "$s }"

            }
        }

        override fun toString(): String {
            return toString(this)
        }

        abstract fun reset()

        protected abstract fun onFeatureChanged()
    }

    //
    //region FeatureSignalLevelRssi
    //

    interface IFeatureSignalLevelRssiListener : IFeatureListener {
        fun onFeatureChanged(feature: IFeatureSignalLevelRssi): Boolean
    }

    interface IFeatureSignalLevelRssi : IFeature {
        fun addListener(listener: IFeatureSignalLevelRssiListener)
        fun removeListener(listener: IFeatureSignalLevelRssiListener)
        val signalLevelRssiRealtime: Int
        val signalLevelRssiSmoothed: Int
    }

    class FeatureSignalLevelRssi(device: BleDevice) : Feature(device), IFeatureSignalLevelRssi {
        companion object {
            private val TAG = TAG(FeatureSignalLevelRssi::class.java)

            const val SIGNAL_LEVEL_RSSI_UNDEFINED = -1

            fun isUndefined(rssi: Int): Boolean {
                return rssi == SIGNAL_LEVEL_RSSI_UNDEFINED
            }

            private const val VERBOSE_LOG = false
        }

        private val listeners = mutableSetOf<IFeatureSignalLevelRssiListener>()

        private var signalLevelRssiRealtimeCurrent = SIGNAL_LEVEL_RSSI_UNDEFINED
        private var signalLevelRssiRealtimePrevious = SIGNAL_LEVEL_RSSI_UNDEFINED
        private var signalLevelRssiSmoothedCurrent = SIGNAL_LEVEL_RSSI_UNDEFINED
        private var signalLevelRssiSmoothedPrevious = SIGNAL_LEVEL_RSSI_UNDEFINED

        override fun toString(): String {
            return toString(
                this,
                ", signalLevelRssiRealtimeCurrent=$signalLevelRssiRealtimeCurrent" +
                        ", signalLevelRssiRealtimePrevious=$signalLevelRssiRealtimePrevious" +
                        ", signalLevelRssiSmoothedCurrent=$signalLevelRssiSmoothedCurrent" +
                        ", signalLevelRssiSmoothedPrevious=$signalLevelRssiSmoothedPrevious}"
            )
        }

        init {
            reset()
        }

        override fun addListener(listener: IFeatureSignalLevelRssiListener) {
            synchronized(listeners) { listeners.add(listener) }
        }

        override fun removeListener(listener: IFeatureSignalLevelRssiListener) {
            synchronized(listeners) { listeners.remove(listener) }
        }

        override fun onFeatureChanged() {
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
            @Suppress("ConstantConditionIf")
            if (VERBOSE_LOG) {
                Log.e(TAG, "setSignalLevelRssi: signalLevelRssiRealtimeCurrent=$signalLevelRssiRealtimeCurrent")
            }
            if (rssi != SIGNAL_LEVEL_RSSI_UNDEFINED) {
                if (signalLevelRssiSmoothedCurrent != SIGNAL_LEVEL_RSSI_UNDEFINED) {
                    rssi = LowPassFilter.update(signalLevelRssiSmoothedCurrent.toLong(), rssi.toLong()).toInt()
                }
            }
            signalLevelRssiSmoothedPrevious = signalLevelRssiSmoothedCurrent
            @Suppress("ConstantConditionIf")
            if (VERBOSE_LOG) {
                Log.e(TAG, "setSignalLevelRssi: signalLevelRssiSmoothedPrevious=$signalLevelRssiSmoothedPrevious")
            }
            changed = if (false && BuildConfig.DEBUG) {
                // NOTE:(pv) In debug mode we want to see the realtime RSSI values
                changed or (signalLevelRssiSmoothedPrevious != rssi)
            } else {
                signalLevelRssiSmoothedPrevious != rssi
            }
            signalLevelRssiSmoothedCurrent = rssi
            @Suppress("ConstantConditionIf")
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

    //
    //endregion FeatureSignalLevelRssi
    //

    //
    //region FeatureBeep
    //

    interface IFeatureBeepListener : IFeatureListener {
        fun onFeatureChanged(feature: IFeatureBeep): Boolean
    }

    interface IFeatureBeepConfiguration {
        val beepDurationMillis: Int
        fun requestBeep(on: Boolean, progress: BleDevice.RequestProgress): Boolean
    }

    interface IFeatureBeep : IFeature, IFeatureBeepConfiguration {
        fun addListener(listener: IFeatureBeepListener)
        fun removeListener(listener: IFeatureBeepListener)
        val isBeeping: Boolean
    }

    class FeatureBeep(device: BleDevice, private val configuration: IFeatureBeepConfiguration) : Feature(device), IFeatureBeep {
        companion object {
            @Suppress("unused")
            private val TAG = TAG(FeatureBeep::class.java)
        }

        private val listeners = mutableSetOf<IFeatureBeepListener>()

        override fun toString(): String {
            return toString(this, ", isBeeping=$isBeeping")
        }

        init {
            reset()
        }

        override fun addListener(listener: IFeatureBeepListener) {
            synchronized(listeners) { listeners.add(listener) }
        }

        override fun removeListener(listener: IFeatureBeepListener) {
            synchronized(listeners) { listeners.remove(listener) }
        }

        override fun onFeatureChanged() {
            synchronized(listeners) {
                for (listener in listeners) {
                    if (listener.onFeatureChanged(this)) {
                        break
                    }
                }
            }
        }

        override fun reset() {
            isBeeping = false
        }

        override var isBeeping: Boolean = false
            set(value) {
                if (isBeeping != value) {
                    field = value
                    onFeatureChanged()
                }
            }

        override val beepDurationMillis: Int
            get() = configuration.beepDurationMillis

        override fun requestBeep(on: Boolean, progress: BleDevice.RequestProgress): Boolean {
            return configuration.requestBeep(on, progress)
        }
    }

    //
    //endregion FeatureBeep
    //
}