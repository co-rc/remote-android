package org.jbanaszczyk.corc.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.TypeConverter;

public class BleTypeConverters {

    // ===== BleDeviceAddress converters =====
    @TypeConverter
    @NonNull
    public static String fromBleDeviceAddress(@Nullable BleDeviceAddress address) {
        return address == null ? BleDeviceAddress.normalizeAddress(null) : address.getValue();
    }

    @TypeConverter
    @NonNull
    public static BleDeviceAddress toBleDeviceAddress(@Nullable String address) {
        return new BleDeviceAddress(address);
    }
}
