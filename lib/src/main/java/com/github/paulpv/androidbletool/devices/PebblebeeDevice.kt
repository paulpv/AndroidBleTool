package com.github.paulpv.androidbletool.devices

import android.util.Log
import com.github.paulpv.androidbletool.devices.Triggers.Trigger
import com.github.paulpv.androidbletool.devices.Triggers.TriggerSignalLevelRssi
import com.github.paulpv.androidbletool.gatt.GattHandler
import com.github.paulpv.androidbletool.utils.ReflectionUtils

class PebblebeeDevice(protected val TAG: String, val modelNumber: Int, private val gattHandler: GattHandler) : Features.IFeatureSignalLevelRssi {
    companion object {
        const val VERBOSE_LOG = false
    }

    interface IPebblebeeDeviceListener : Features.IFeatureSignalLevelRssiListener

    private val macAddressLong = gattHandler.deviceAddressLong
    val macAddressString = gattHandler.deviceAddressString

    private val featureSignalLevelRssi = Features.FeatureSignalLevelRssi(this)

    constructor(other: PebblebeeDevice) : this(other.TAG, other.modelNumber, other.gattHandler) {
        featureSignalLevelRssi.copy(other.featureSignalLevelRssi)
    }

    override fun toString(): String {
        return toString(false)
    }

    fun toString(simple: Boolean): String {
        val sb = StringBuilder()
        sb.append(ReflectionUtils.instanceName(this))
        sb.append('{')
            .append("macAddressString=").append(macAddressString)
            .append(", macAddressLong=").append(macAddressLong)
            .append(", modelNumber=").append(PebblebeeDevices.PebblebeeDeviceModelNumbers.toString(modelNumber))
        if (!simple) {
            sb.append(", featureSignalLevelRssi=").append(featureSignalLevelRssi)
        }
        sb.append('}')
        return sb.toString()
    }

    //
    //
    //

    fun addListener(listener: IPebblebeeDeviceListener) {
        addListener(listener as Features.IFeatureSignalLevelRssiListener)
    }

    fun removeListener(listener: IPebblebeeDeviceListener) {
        removeListener(listener as Features.IFeatureSignalLevelRssiListener)
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

    fun update(triggers: MutableSet<Trigger<*>>, forceRssiChange: Boolean): Boolean {
        var immediate = false

        var forcedChanged: Boolean
        var changed: Boolean

        synchronized(updateSyncLock) {
            val it = triggers.iterator()
            while (it.hasNext()) {
                val trigger = it.next()
                @Suppress("ConstantConditionIf")
                if (VERBOSE_LOG) {
                    Log.v(TAG, "$macAddressString update: trigger=$trigger")
                }
                update(trigger)
                if (trigger.isChanged) {
                    forcedChanged = false
                    changed = true
                } else {
                    if (forceRssiChange && trigger is TriggerSignalLevelRssi) {
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
                        if (VERBOSE_LOG) {
                            Log.v(TAG, "$macAddressString update: trigger CHANGED and IMMEDIATE")
                        }
                        immediate = true
                    } else {
                        @Suppress("ConstantConditionIf")
                        if (VERBOSE_LOG) {
                            if (forcedChanged) {
                                Log.v(TAG, "$macAddressString update: trigger *FORCED CHANGED*")
                            } else {
                                Log.v(TAG, "$macAddressString update: trigger CHANGED")
                            }
                        }
                    }
                } else {
                    @Suppress("ConstantConditionIf")
                    if (VERBOSE_LOG) {
                        Log.v(TAG, "$macAddressString update: trigger NOT CHANGED; removing")
                    }
                    it.remove()
                }
            }
        }

        return immediate
    }

    fun update(trigger: Trigger<*>): Boolean {
        if (trigger is TriggerSignalLevelRssi) {
            featureSignalLevelRssi.setSignalLevelRssi(trigger)
            return true
        }
        return false
    }
}