package org.jbanaszczyk.corc.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.jbanaszczyk.corc.ble.exceptions.InvalidAddressException;

public final class BleDeviceAddress {

    private static final String EMPTY_ADDRESS = "FF:FF:FF:FF:FF:FF";

    private static final BleDeviceAddress EMPTY = new BleDeviceAddress();

    @NonNull
    private final String address;

    private BleDeviceAddress(@NonNull String address, boolean alreadyNormalized) {
        this.address = alreadyNormalized ? address : normalizeAddress(address);
    }

    public BleDeviceAddress() {
        this(EMPTY_ADDRESS, true);
    }

    public BleDeviceAddress(@Nullable String address) {
        this(address == null ? EMPTY_ADDRESS : address, false);
    }

    public BleDeviceAddress(@Nullable BleDeviceAddress other) {
        this(other == null ? EMPTY_ADDRESS : other.address, true);
    }

    @NonNull
    public static BleDeviceAddress of(@Nullable String address) throws InvalidAddressException {
        validateAddress(address);
        return new BleDeviceAddress(address, true);
    }

    @NonNull
    public static BleDeviceAddress getAddressFromDevice(@NonNull BluetoothGatt gatt) {
        var device = gatt.getDevice();
        if (device == null) {
            return EMPTY;
        }
        return new BleDeviceAddress(device.getAddress());
    }

    @NonNull
    public static String normalizeAddress(@Nullable String address) {
        return address == null || !BluetoothAdapter.checkBluetoothAddress(address)
                ? EMPTY_ADDRESS
                : address;
    }

    public static boolean isEmpty(@Nullable String address) {
        return EMPTY_ADDRESS.equals(normalizeAddress(address));
    }

    public static void validateAddress(@Nullable String address) throws InvalidAddressException {
        if (address == null || !BluetoothAdapter.checkBluetoothAddress(address)) {
            throw new InvalidAddressException(address, "Invalid BLE address");
        }
    }

    public boolean isEmpty() {
        return isEmpty(address);
    }

    public void validate() throws InvalidAddressException {
        validateAddress(address);
    }

    @NonNull
    public String getValue() {
        return address;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BleDeviceAddress that)) return false;
        return address.equals(that.address);
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return address;
    }
}
