package org.jbanaszczyk.corc.ble;

import android.bluetooth.BluetoothAdapter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.Objects;

@Entity(tableName = "ble_devices")
public class BleDevice {

    public static final String EMPTY_ADDRESS = "FF:FF:FF:FF:FF:FF";

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "address")
    private String address = EMPTY_ADDRESS;

    public BleDevice() {
    }

    @Ignore
    public BleDevice(@Nullable String address) {
        this.address = normalizeAddress(address);
    }

    @NonNull
    public static String normalizeAddress(@Nullable String address) {
        //noinspection DataFlowIssue
        return BluetoothAdapter.checkBluetoothAddress(address)
                ? address
                : EMPTY_ADDRESS;
    }

    public static boolean isEmpty(@Nullable String address) {
        return EMPTY_ADDRESS.equals(normalizeAddress(address));
    }

    public boolean isEmpty() {
        return isEmpty(address);
    }

    @NonNull
    @Override
    public String toString() {
        return "BleDevice{address='" + address + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BleDevice bleDevice)) return false;
        return address.equals(bleDevice.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }
}
