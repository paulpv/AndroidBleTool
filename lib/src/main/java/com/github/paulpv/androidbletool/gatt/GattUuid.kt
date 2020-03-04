package com.github.paulpv.androidbletool.gatt

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.ParcelUuid
import com.github.paulpv.androidbletool.utils.Utils
import java.util.*

class GattUuid(val uuid: UUID, val name: String) {
    @Suppress("MemberVisibilityCanBePrivate")
    val assignedNumber = GattUuids.getAssignedNumber(this.uuid)

    constructor(assignedNumber: Int, name: String) : this(GattUuids.assignedNumberToUUID(assignedNumber), name)
    constructor(uuid: String, name: String) : this(UUID.fromString(uuid), name)

    override fun toString() = "${Utils.quote(name)}(${String.format("0x%04X", assignedNumber)})"

    override fun equals(other: Any?) = when (other) {
        is GattUuid -> equals(other)
        is UUID -> equals(other)
        is ParcelUuid -> equals(other)
        is BluetoothGattService -> equals(other)
        is BluetoothGattCharacteristic -> equals(other)
        else -> super.equals(other)
    }

    fun equals(o: GattUuid) = equals(o.uuid)
    fun equals(o: ParcelUuid) = equals(o.uuid)
    fun equals(o: BluetoothGattService) = equals(o.uuid)
    fun equals(o: BluetoothGattCharacteristic) = equals(o.uuid)
    fun equals(o: UUID) = uuid == o

    override fun hashCode() = uuid.hashCode()

    @Suppress("unused")
    val parcelable: ParcelUuid
        get() = ParcelUuid(uuid)
}