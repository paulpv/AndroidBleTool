package com.github.paulpv.androidbletool.gatt;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

import com.github.paulpv.androidbletool.utils.Utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.UUID;

/**
 * From:
 * https://developer.bluetooth.org/gatt/services/Pages/ServicesHome.aspx
 * https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicsHome.aspx
 * <p>
 * Other References:
 * https://github.com/movisens/SmartGattLib/blob/master/src/main/java/com/movisens/smartgattlib/Service.java
 * https://github.com/movisens/SmartGattLib/blob/master/src/main/java/com/movisens/smartgattlib/Characteristic.java
 */
public class GattUuids {
    private static final String TAG = Utils.Companion.TAG(GattUuids.class);

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    private @interface SkipLookup {
    }

    public static final GattUuid ALERT_NOTIFICATION_SERVICE = new GattUuid(0x1811, "Alert Notification Service");
    public static final GattUuid ALERT_CATEGORY_ID = new GattUuid(0x2a43, "Alert Category ID");
    public static final GattUuid ALERT_CATEGORY_ID_BIT_MASK = new GattUuid(0x2a42, "Alert Category ID Bit Mask");
    // https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.alert_level.xml
    public static final GattUuid ALERT_LEVEL = new GattUuid(0x2a06, "Alert Level");
    public static final int ALERT_LEVEL_NONE = 0x00;
    public static final int ALERT_LEVEL_MILD = 0x01;
    public static final int ALERT_LEVEL_HIGH = 0x02;
    public static final GattUuid ALERT_NOTIFICATION_CONTROL_POINT = new GattUuid(0x2a44, "Alert Notification Control Point");
    public static final GattUuid ALERT_STATUS = new GattUuid(0x2a3f, "Alert Status");
    public static final GattUuid NEW_ALERT = new GattUuid(0x2a46, "New Alert");
    //
    public static final GattUuid BATTERY_SERVICE = new GattUuid(0x180f, "Battery Service");
    public static final GattUuid BATTERY_LEVEL = new GattUuid(0x2a19, "Battery Level");
    //
    public static final GattUuid BLOOD_PRESSURE_SERVICE = new GattUuid(0x1810, "Blood Pressure Service");
    public static final GattUuid BLOOD_PRESSURE_MEASUREMENT = new GattUuid(0x2a35, "Blood Pressure Measurement");
    //
    public static final GattUuid CYCLING_SPEED_AND_CADENCE_SERVICE = new GattUuid(0x1816, "Cycling Speed and Cadence Service");
    public static final GattUuid CYCLING_SPEED_AND_CADENCE_MEASUREMENT = new GattUuid(0x2a5b, "Cycling Speed and Cadence Measurement");
    public static final GattUuid CYCLING_SPEED_AND_CADENCE_FEATURE = new GattUuid(0x2a5c, "Cycling Speed and Cadence Feature");
    public static final GattUuid CYCLING_SPEED_AND_CADENCE_CONTROL_POINT = new GattUuid(0x2a55, "Speed and Cadence Control Point");
    public static final GattUuid SENSOR_LOCATION = new GattUuid(0x2a5d, "Sensor Location");
    //
    public static final GattUuid DEVICE_INFORMATION_SERVICE = new GattUuid(0x180A, "Device Information Service");
    public static final GattUuid MANUFACTURER_NAME = new GattUuid(0x2A29, "Manufacturer Name String");
    public static final GattUuid MODEL_NUMBER = new GattUuid(0x2a24, "Model Number String");
    public static final GattUuid SERIAL_NUMBER = new GattUuid(0x2a25, "Serial Number String");
    public static final GattUuid HARDWARE_REVISION = new GattUuid(0x2a27, "Hardware Revision String");
    public static final GattUuid FIRMWARE_REVISION = new GattUuid(0x2a26, "Firmware Revision String");
    public static final GattUuid SOFTWARE_REVISION = new GattUuid(0x2a28, "Software Revision String");
    public static final GattUuid PNP_ID = new GattUuid(0x2a50, "PnP ID");
    //
    public static final GattUuid ENVIROMENTAL_SENSING_SERVICE = new GattUuid(0x181A, "Environmental Sensing Service");
    public static final GattUuid TEMPERATURE_CHARACTERISTIC = new GattUuid(0x2A6E, "Temperature");
    //
    //
    // https://www.bluetooth.org/en-us/specification/assigned-numbers/generic-attribute-profile
    //
    public static final GattUuid GENERIC_ACCESS_SERVICE = new GattUuid(0x1800, "Generic Access Service");
    public static final GattUuid DEVICE_NAME = new GattUuid(0x2A00, "Device Name");
    public static final GattUuid APPEARANCE = new GattUuid(0x2A01, "Appearance");
    public static final GattUuid PERIPHERAL_PREFERRED_CONNECTION_PARAMETERS = new GattUuid(0x2A04, "Peripheral Preferred Connection Parameters");
    public static final GattUuid SERVICE_CHANGED = new GattUuid(0x2A05, "Service Changed");
    public static final GattUuid GENERIC_ATTRIBUTE_SERVICE = new GattUuid(0x1801, "Generic Attribute Service");
    //
    public static final GattUuid HEART_RATE_SERVICE = new GattUuid(0x180d, "Heart Rate Service");
    public static final GattUuid HEART_RATE_MEASUREMENT = new GattUuid(0x2a37, "Heart Rate Measurement");
    public static final GattUuid BODY_SENSOR_LOCATION = new GattUuid(0x2a38, "Body Sensor Location");
    public static final GattUuid CLIENT_CHARACTERISTIC_CONFIG = new GattUuid(0x2902, "Client Characteristic Config");
    //
    public static final GattUuid IMMEDIATE_ALERT_SERVICE = new GattUuid(0x1802, "Immediate Alert Service");
    //
    public static final GattUuid LINK_LOSS_SERVICE = new GattUuid(0x1803, "Link Loss Service");
    //
    public static final GattUuid RUNNING_SPEED_AND_CADENCE_SERVICE = new GattUuid(0x1814, "Running Speed and Cadence Service");
    public static final GattUuid RUNNING_SPEED_AND_CADENCE_MEASUREMENT = new GattUuid(0x2a53, "Running Speed and Cadence Measurement");
    //
    public static final GattUuid PEBBLEBEE_HONEY_TEMPERATURE_SERVICE = new GattUuid("0000AB04-D105-11E1-9B23-00025B00A5A5", "Pebblebee Honey Temperature Service");
    public static final GattUuid PEBBLEBEE_HONEY_TEMPERATURE_CHARACTERISTIC = new GattUuid("0000AB07-D108-11E1-9B23-00025B00A5A5", "Pebblebee Honey Temperature");
    //
    public static final GattUuid PEBBLEBEE_MOTION_DATA_SERVICE = new GattUuid(0x1901, "Pebblebee Motion Data Service");
    public static final GattUuid PEBBLEBEE_MOTION_DATA_CHARACTERISTIC = new GattUuid(0x2B01, "Pebblebee Motion Data");
    public static final GattUuid PEBBLEBEE_MOTION_EVENT_SERVICE = new GattUuid(0x1902, "Pebblebee Motion Event Service");
    public static final GattUuid PEBBLEBEE_MOTION_EVENT_CHARACTERISTIC = new GattUuid(0x2B02, "Pebblebee Motion Event");
    //
    public static final GattUuid PEBBLEBEE_TUNNEL_SERVICE = new GattUuid(0x1903, "Pebblebee Tunnel Service");
    public static final GattUuid PEBBLEBEE_TUNNEL_CHARACTERISTIC = new GattUuid(0x2B03, "Pebblebee Tunnel");
    public static final GattUuid PEBBLEBEE_DEBUG_SERVICE = new GattUuid(0x1904, "Pebblebee Debug Service");
    public static final GattUuid PEBBLEBEE_DEBUG_CHARACTERISTIC = new GattUuid(0x2B04, "Pebblebee Debug");
    //
    public static final GattUuid PEBBLEBEE_FINDER_CHARACTERISTIC1 = new GattUuid(0x2C01, "Pebblebee Finder Characteristic 1");
    public static final GattUuid PEBBLEBEE_FINDER_CHARACTERISTIC2 = new GattUuid(0x2C02, "Pebblebee Finder Characteristic 2");
    //
    public static final GattUuid PEBBLEBEE_STONE_SERVICE = new GattUuid(0x8888, "Pebblebee Stone Service");
    public static final GattUuid PEBBLEBEE_FINDER_SERVICE = new GattUuid(0xFA25, "Pebblebee Finder Service");
    @SkipLookup // 0x1901 clashes with Dragon/Hornet PEBBLEBEE_MOTION_DATA_SERVICE
    public static final GattUuid PEBBLEBEE_BUZZER1_SERVICE = new GattUuid(0x1901, "Pebblebee Buzzer1 Service");
    public static final GattUuid PEBBLEBEE_BUZZER1_STOP_LIGHT_SOUND_CHARACTERISTIC = new GattUuid(0x2B34, "Pebblebee Buzzer1 Stop Find Characteristic");
    public static final GattUuid PEBBLEBEE_BUZZER2_SERVICE = new GattUuid(0xFB25, "Pebblebee Buzzer2 Service");
    //
    public static final GattUuid PEBBLEBEE_OVER_THE_AIR_UPDATE_SERVICE = new GattUuid("00001016-D102-11E1-9B23-00025B00A5A5", "Pebblebee Over-The-Air Update Service");
    public static final GattUuid PEBBLEBEE_OTA_UNKNOWN1 = new GattUuid("00001011-D102-11E1-9B23-00025B00A5A5", "PEBBLEBEE_OTA_UNKNOWN1");
    public static final GattUuid PEBBLEBEE_CURRENT_APPLICATION_CHARACTERISTIC = new GattUuid("00001013-D102-11E1-9B23-00025B00A5A5", "Current Application");
    public static final GattUuid PEBBLEBEE_DATA_TRANSFER_CHARACTERISTIC = new GattUuid("00001014-D102-11E1-9B23-00025B00A5A5", "Data Transfer");
    public static final GattUuid PEBBLEBEE_READ_CONFIG_STORE_BLOCK_CHARACTERISTIC = new GattUuid("00001018-D102-11E1-9B23-00025B00A5A5", "Read Config Store Block");
    //
    public static final GattUuid TX_POWER_SERVICE = new GattUuid(0x1804, "Tx Power Service");
    public static final GattUuid TX_POWER_LEVEL = new GattUuid(0x2a07, "Tx Power Level");

    private GattUuids() {
    }

    public static int getAssignedNumber(UUID uuid) {
        return (int) ((uuid.getMostSignificantBits() & 0x0000FFFF00000000L) >> 32);
    }

    private static final long GATT_LEAST_SIGNIFICANT_BITS = 0x800000805f9b34fbL;

    public static UUID assignedNumberToUUID(int assignedNumber) {
        return new UUID(((long) assignedNumber << 32) | 0x1000, GATT_LEAST_SIGNIFICANT_BITS);
    }

    // Lookup table to allow reverse sLookup.
    private static HashMap<UUID, GattUuid> sLookup;

    /**
     * Reverse look up UUID -&gt; GattUuid
     *
     * @param uuid The UUID to get a look up a GattUuid value for.
     * @return GattUuid that matches the given UUID, or null if not found
     */
    public static GattUuid get(UUID uuid) {
        if (sLookup == null) {
            //
            // Populate the sLookup table upon first sLookup
            //

            sLookup = new HashMap<>();

            for (Field field : GattUuids.class.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isPublic(modifiers) &&
                        Modifier.isStatic(modifiers) &&
                        Modifier.isFinal(modifiers) &&
                        field.getType() == GattUuid.class &&
                        field.getAnnotation(SkipLookup.class) == null) {
                    try {
                        GattUuid value = (GattUuid) field.get(null);
                        if (sLookup.put(value.getUuid(), value) != null) {
                            Log.e(TAG, "get: duplicate UUID defined for " + value.getUuid());
                        }
                    } catch (IllegalAccessException e) {
                        // unreachable
                    }
                }
            }
        }
        return sLookup.get(uuid);
    }

    public static String toString(BluetoothGattService service) {
        return (service == null) ? "null" : toString(service.getUuid());
    }

    public static String toString(BluetoothGattCharacteristic characteristic) {
        return (characteristic == null) ? "null" : toString(characteristic.getUuid());
    }

    public static String toString(BluetoothGattDescriptor descriptor) {
        return (descriptor == null) ? "null" : toString(descriptor.getUuid());
    }

    public static String toString(UUID uuid) {
        GattUuid gattUuid = get(uuid);
        if (gattUuid != null) {
            return gattUuid.toString();
        }
        return uuid == null ? "null" : uuid.toString();
    }
}