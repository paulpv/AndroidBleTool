package com.github.paulpv.androidbletool.gatt;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.ParcelUuid;

import com.github.paulpv.androidbletool.utils.Utils;

import java.util.UUID;

public class GattUuid {
    private final int mAssignedNumber;
    private final String mName;
    private final UUID mUuid;

    public GattUuid(int assignedNumber, String name) {
        this(GattUuids.assignedNumberToUUID(assignedNumber), name);
    }

    public GattUuid(String uuid, String name) {
        this(UUID.fromString(uuid), name);
    }

    public GattUuid(UUID uuid, String name) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid must not be null");
        }
        if (Utils.Companion.isNullOrEmpty(name)) {
            throw new IllegalArgumentException("name must not be null or empty");
        }

        mUuid = uuid;
        mName = name;
        mAssignedNumber = GattUuids.getAssignedNumber(mUuid);
    }

    @Override
    public String toString() {
        return Utils.Companion.quote(mName) + '(' + String.format("0x%04X", mAssignedNumber) + ')';
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GattUuid) {
            return equals((GattUuid) o);
        } else if (o instanceof UUID) {
            return equals((UUID) o);
        } else if (o instanceof ParcelUuid) {
            return equals((ParcelUuid) o);
        } else if (o instanceof BluetoothGattService) {
            return equals((BluetoothGattService) o);
        } else if (o instanceof BluetoothGattCharacteristic) {
            return equals((BluetoothGattCharacteristic) o);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(GattUuid o) {
        return o != null && equals(o.mUuid);
    }

    public boolean equals(ParcelUuid o) {
        return o != null && equals(o.getUuid());
    }

    public boolean equals(BluetoothGattService o) {
        return o != null && equals(o.getUuid());
    }

    public boolean equals(BluetoothGattCharacteristic o) {
        return o != null && equals(o.getUuid());
    }

    public boolean equals(UUID o) {
        return mUuid.equals(o);
    }

    public int getAssignedNumber() {
        return mAssignedNumber;
    }

    public UUID getUuid() {
        return mUuid;
    }

    public String getName() {
        return mName;
    }

    public ParcelUuid getParcelable() {
        return new ParcelUuid(mUuid);
    }
}
