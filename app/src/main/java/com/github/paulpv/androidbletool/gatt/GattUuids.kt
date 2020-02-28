package com.github.paulpv.androidbletool.gatt

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.util.Log
import com.github.paulpv.androidbletool.utils.Utils.Companion.TAG
import java.lang.reflect.Modifier
import java.util.*

/**
 * From:
 * https://developer.bluetooth.org/gatt/services/Pages/ServicesHome.aspx
 * https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicsHome.aspx
 *
 *
 * Other References:
 * https://github.com/movisens/SmartGattLib/blob/master/src/main/java/com/movisens/smartgattlib/Service.java
 * https://github.com/movisens/SmartGattLib/blob/master/src/main/java/com/movisens/smartgattlib/Characteristic.java
 */
@Suppress("unused")
class GattUuids private constructor() {
    companion object {
        private val TAG = TAG(GattUuids::class.java)

        @Retention(AnnotationRetention.RUNTIME)
        @Target(AnnotationTarget.FIELD)
        private annotation class SkipLookup

        //@formatter:off
        val ALERT_NOTIFICATION_SERVICE                        = GattUuid(0x1811, "Alert Notification Service")
        val ALERT_CATEGORY_ID                                 = GattUuid(0x2a43, "Alert Category ID")
        val ALERT_CATEGORY_ID_BIT_MASK                        = GattUuid(0x2a42, "Alert Category ID Bit Mask")
        // https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.alert_level.xml
        val ALERT_LEVEL                                       = GattUuid(0x2a06, "Alert Level")
        const val ALERT_LEVEL_NONE                            = 0x00
        const val ALERT_LEVEL_MILD                            = 0x01
        const val ALERT_LEVEL_HIGH                            = 0x02
        val ALERT_NOTIFICATION_CONTROL_POINT                  = GattUuid(0x2a44, "Alert Notification Control Point")
        val ALERT_STATUS                                      = GattUuid(0x2a3f, "Alert Status")
        val NEW_ALERT                                         = GattUuid(0x2a46, "New Alert")
        //
        val BATTERY_SERVICE                                   = GattUuid(0x180f, "Battery Service")
        val BATTERY_LEVEL                                     = GattUuid(0x2a19, "Battery Level")
        //
        val BLOOD_PRESSURE_SERVICE                            = GattUuid(0x1810, "Blood Pressure Service")
        val BLOOD_PRESSURE_MEASUREMENT                        = GattUuid(0x2a35, "Blood Pressure Measurement")
        //
        val CYCLING_SPEED_AND_CADENCE_SERVICE                 = GattUuid(0x1816, "Cycling Speed and Cadence Service")
        val CYCLING_SPEED_AND_CADENCE_MEASUREMENT             = GattUuid(0x2a5b, "Cycling Speed and Cadence Measurement")
        val CYCLING_SPEED_AND_CADENCE_FEATURE                 = GattUuid(0x2a5c, "Cycling Speed and Cadence Feature")
        val CYCLING_SPEED_AND_CADENCE_CONTROL_POINT           = GattUuid(0x2a55, "Speed and Cadence Control Point")
        val SENSOR_LOCATION                                   = GattUuid(0x2a5d, "Sensor Location")
        //
        val DEVICE_INFORMATION_SERVICE                        = GattUuid(0x180A, "Device Information Service")
        val MANUFACTURER_NAME                                 = GattUuid(0x2A29, "Manufacturer Name String")
        val MODEL_NUMBER                                      = GattUuid(0x2a24, "Model Number String")
        val SERIAL_NUMBER                                     = GattUuid(0x2a25, "Serial Number String")
        val HARDWARE_REVISION                                 = GattUuid(0x2a27, "Hardware Revision String")
        val FIRMWARE_REVISION                                 = GattUuid(0x2a26, "Firmware Revision String")
        val SOFTWARE_REVISION                                 = GattUuid(0x2a28, "Software Revision String")
        val PNP_ID                                            = GattUuid(0x2a50, "PnP ID")
        //
        val ENVIROMENTAL_SENSING_SERVICE                      = GattUuid(0x181A, "Environmental Sensing Service")
        val TEMPERATURE_CHARACTERISTIC                        = GattUuid(0x2A6E, "Temperature")
        //
        // https://www.bluetooth.org/en-us/specification/assigned-numbers/generic-attribute-profile
        //
        val GENERIC_ACCESS_SERVICE                            = GattUuid(0x1800, "Generic Access Service")
        val DEVICE_NAME                                       = GattUuid(0x2A00, "Device Name")
        val APPEARANCE                                        = GattUuid(0x2A01, "Appearance")
        val PERIPHERAL_PREFERRED_CONNECTION_PARAMETERS        = GattUuid(0x2A04, "Peripheral Preferred Connection Parameters")
        val SERVICE_CHANGED                                   = GattUuid(0x2A05, "Service Changed")
        val GENERIC_ATTRIBUTE_SERVICE                         = GattUuid(0x1801, "Generic Attribute Service")
        //
        val HEART_RATE_SERVICE                                = GattUuid(0x180d, "Heart Rate Service")
        val HEART_RATE_MEASUREMENT                            = GattUuid(0x2a37, "Heart Rate Measurement")
        val BODY_SENSOR_LOCATION                              = GattUuid(0x2a38, "Body Sensor Location")
        val CLIENT_CHARACTERISTIC_CONFIG                      = GattUuid(0x2902, "Client Characteristic Config")
        //
        val IMMEDIATE_ALERT_SERVICE                           = GattUuid(0x1802, "Immediate Alert Service")
        //
        val LINK_LOSS_SERVICE                                 = GattUuid(0x1803, "Link Loss Service")
        //
        val RUNNING_SPEED_AND_CADENCE_SERVICE                 = GattUuid(0x1814, "Running Speed and Cadence Service")
        val RUNNING_SPEED_AND_CADENCE_MEASUREMENT             = GattUuid(0x2a53, "Running Speed and Cadence Measurement")
        //
        val PEBBLEBEE_HONEY_TEMPERATURE_SERVICE               = GattUuid("0000AB04-D105-11E1-9B23-00025B00A5A5", "Pebblebee Honey Temperature Service")
        val PEBBLEBEE_HONEY_TEMPERATURE_CHARACTERISTIC        = GattUuid("0000AB07-D108-11E1-9B23-00025B00A5A5", "Pebblebee Honey Temperature")
        //
        val PEBBLEBEE_MOTION_DATA_SERVICE                     = GattUuid(0x1901, "Pebblebee Motion Data Service")
        val PEBBLEBEE_MOTION_DATA_CHARACTERISTIC              = GattUuid(0x2B01, "Pebblebee Motion Data")
        val PEBBLEBEE_MOTION_EVENT_SERVICE                    = GattUuid(0x1902, "Pebblebee Motion Event Service")
        val PEBBLEBEE_MOTION_EVENT_CHARACTERISTIC             = GattUuid(0x2B02, "Pebblebee Motion Event")
        //
        val PEBBLEBEE_TUNNEL_SERVICE                          = GattUuid(0x1903, "Pebblebee Tunnel Service")
        val PEBBLEBEE_TUNNEL_CHARACTERISTIC                   = GattUuid(0x2B03, "Pebblebee Tunnel")
        val PEBBLEBEE_DEBUG_SERVICE                           = GattUuid(0x1904, "Pebblebee Debug Service")
        val PEBBLEBEE_DEBUG_CHARACTERISTIC                    = GattUuid(0x2B04, "Pebblebee Debug")
        //
        val PEBBLEBEE_FINDER_CHARACTERISTIC1                  = GattUuid(0x2C01, "Pebblebee Finder Characteristic 1")
        val PEBBLEBEE_FINDER_CHARACTERISTIC2                  = GattUuid(0x2C02, "Pebblebee Finder Characteristic 2")
        //
        val PEBBLEBEE_STONE_SERVICE                           = GattUuid(0x8888, "Pebblebee Stone Service")
        val PEBBLEBEE_FINDER_SERVICE                          = GattUuid(0xFA25, "Pebblebee Finder Service")
        @SkipLookup // 0x1901 clashes with Dragon/Hornet PEBBLEBEE_MOTION_DATA_SERVICE
        val PEBBLEBEE_BUZZER1_SERVICE                         = GattUuid(0x1901, "Pebblebee Buzzer1 Service")
        val PEBBLEBEE_BUZZER1_STOP_LIGHT_SOUND_CHARACTERISTIC = GattUuid(0x2B34, "Pebblebee Buzzer1 Stop Find Characteristic")
        val PEBBLEBEE_BUZZER2_SERVICE                         = GattUuid(0xFB25, "Pebblebee Buzzer2 Service")
        //
        val PEBBLEBEE_OVER_THE_AIR_UPDATE_SERVICE             = GattUuid("00001016-D102-11E1-9B23-00025B00A5A5", "Pebblebee Over-The-Air Update Service")
        val PEBBLEBEE_OTA_UNKNOWN1                            = GattUuid("00001011-D102-11E1-9B23-00025B00A5A5", "PEBBLEBEE_OTA_UNKNOWN1")
        val PEBBLEBEE_CURRENT_APPLICATION_CHARACTERISTIC      = GattUuid("00001013-D102-11E1-9B23-00025B00A5A5", "Current Application")
        val PEBBLEBEE_DATA_TRANSFER_CHARACTERISTIC            = GattUuid("00001014-D102-11E1-9B23-00025B00A5A5", "Data Transfer")
        val PEBBLEBEE_READ_CONFIG_STORE_BLOCK_CHARACTERISTIC  = GattUuid("00001018-D102-11E1-9B23-00025B00A5A5", "Read Config Store Block")
        //
        val TX_POWER_SERVICE                                  = GattUuid(0x1804, "Tx Power Service")
        val TX_POWER_LEVEL                                    = GattUuid(0x2a07, "Tx Power Level")
        //@formatter:on

        //
        //
        //

        fun getAssignedNumber(uuid: UUID) = (uuid.mostSignificantBits and 0x0000FFFF00000000L shr 32).toInt()

        private const val GATT_LEAST_SIGNIFICANT_BITS = -0x7fffff7fa064cb05L

        fun assignedNumberToUUID(assignedNumber: Int) = UUID(assignedNumber.toLong() shl 32 or 0x1000, GATT_LEAST_SIGNIFICANT_BITS)

        //
        //
        //

        // Lookup table to allow reverse sLookup.
        private val sLookup = mutableMapOf<UUID, GattUuid>()

        /**
         * Reverse look up UUID -&gt; GattUuid
         *
         * @param uuid The UUID to get a look up a GattUuid value for.
         * @return GattUuid that matches the given UUID, or null if not found
         */
        operator fun get(uuid: UUID): GattUuid? {
            if (sLookup.isEmpty()) {
                //
                // Populate the sLookup table upon first sLookup
                //
                for (field in GattUuids::class.java.declaredFields) {
                    val modifiers = field.modifiers
                    if (Modifier.isPublic(modifiers) &&
                        Modifier.isStatic(modifiers) &&
                        Modifier.isFinal(modifiers) && field.type == GattUuid::class.java && field.getAnnotation(SkipLookup::class.java) == null
                    ) {
                        try {
                            val value = field[null] as GattUuid
                            if (sLookup.put(value.uuid, value) != null) {
                                Log.e(TAG, "get: duplicate UUID defined for " + value.uuid)
                            }
                        } catch (e: IllegalAccessException) {
                            // unreachable
                        }
                    }
                }
            }
            return sLookup[uuid]
        }

        fun toString(service: BluetoothGattService?) = toString(service?.uuid)
        fun toString(characteristic: BluetoothGattCharacteristic?) = toString(characteristic?.uuid)
        fun toString(descriptor: BluetoothGattDescriptor?) = toString(descriptor?.uuid)
        fun toString(uuid: UUID?): String {
            if (uuid == null) return "null"
            return get(uuid)?.toString() ?: uuid.toString()
        }
    }
}