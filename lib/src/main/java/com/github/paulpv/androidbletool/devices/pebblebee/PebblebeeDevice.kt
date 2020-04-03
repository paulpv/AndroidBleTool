package com.github.paulpv.androidbletool.devices.pebblebee

import android.util.Log
import com.github.paulpv.androidbletool.BleDevice
import com.github.paulpv.androidbletool.BuildConfig
import com.github.paulpv.androidbletool.devices.Features
import com.github.paulpv.androidbletool.devices.Triggers
import com.github.paulpv.androidbletool.gatt.GattHandler

open class PebblebeeDevice(
    protected val TAG: String,
    @Suppress("MemberVisibilityCanBePrivate") val modelNumber: Int,
    gattHandler: GattHandler
) : BleDevice(gattHandler),
    Features.IFeatureSignalLevelRssi {
    companion object {
        private val DEBUG_LOG_UPDATE = false && BuildConfig.DEBUG
    }

    private val featureSignalLevelRssi = Features.FeatureSignalLevelRssi(this)

    override fun toString(): String {
        return toString(false)
    }

    fun toString(simple: Boolean): String {
        val sb = StringBuilder()
            .append(", modelNumber=").append(
                Pebblebee.DeviceModelNumber.toString(
                    modelNumber
                )
            )
        if (!simple) {
            sb.append(", featureSignalLevelRssi=").append(featureSignalLevelRssi)
        }
        return toString(this, sb.toString())
    }

    override fun reset() {
        super.reset()
        featureSignalLevelRssi.reset()
    }


    //
    //region IFeature
    //

    override val device: PebblebeeDevice
        get() = this

    //
    //endregion IFeature
    //

    //
    //region IFeatureSignalLevelRssi
    //

    override fun addListener(listener: Features.IFeatureSignalLevelRssiListener) {
        featureSignalLevelRssi.addListener(listener)
    }

    override fun removeListener(listener: Features.IFeatureSignalLevelRssiListener) {
        featureSignalLevelRssi.removeListener(listener)
    }

    override val signalLevelRssiRealtime: Int
        get() = featureSignalLevelRssi.signalLevelRssiRealtime
    override val signalLevelRssiSmoothed: Int
        get() = featureSignalLevelRssi.signalLevelRssiSmoothed

    //
    //endregion IFeatureSignalLevelRssi
    //

    private val updateSyncLock = Any()

    fun update(triggers: MutableSet<Triggers.Trigger<*>>, forceRssiChange: Boolean = false): Boolean {
        var immediate = false

        var forcedChanged: Boolean
        var changed: Boolean

        synchronized(updateSyncLock) {
            val it = triggers.iterator()
            while (it.hasNext()) {
                val trigger = it.next()
                @Suppress("ConstantConditionIf")
                if (DEBUG_LOG_UPDATE) {
                    Log.v(TAG, "$macAddressString update: trigger=$trigger")
                }
                update(trigger)
                if (trigger.isChanged) {
                    forcedChanged = false
                    changed = true
                } else {
                    if (forceRssiChange && trigger is Triggers.TriggerSignalLevelRssi) {
                        forcedChanged = true
                        changed = true
                    } else {
                        forcedChanged = false
                        changed = false
                    }
                }
                if (changed || forcedChanged) {
                    if (trigger.isImmediate) {
                        @Suppress("ConstantConditionIf")
                        if (DEBUG_LOG_UPDATE) {
                            Log.v(TAG, "$macAddressString update: trigger CHANGED and IMMEDIATE")
                        }
                        immediate = true
                    } else {
                        @Suppress("ConstantConditionIf")
                        if (DEBUG_LOG_UPDATE) {
                            if (forcedChanged) {
                                Log.v(TAG, "$macAddressString update: trigger *FORCED CHANGED*")
                            } else {
                                Log.v(TAG, "$macAddressString update: trigger CHANGED")
                            }
                        }
                    }
                } else {
                    @Suppress("ConstantConditionIf")
                    if (DEBUG_LOG_UPDATE) {
                        Log.v(TAG, "$macAddressString update: trigger NOT CHANGED; removing")
                    }
                    it.remove()
                }
            }
        }

        return immediate
    }

    protected open fun update(trigger: Triggers.Trigger<*>): Boolean {
        if (trigger is Triggers.TriggerSignalLevelRssi) {
            featureSignalLevelRssi.setSignalLevelRssi(trigger)
            return true
        }
        return false
    }
}