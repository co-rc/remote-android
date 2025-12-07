package org.jbanaszczyk.corc.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.TypeConverter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BleTypeConverters {

    private static final char CSV_SEPARATOR = ',';

    // ===== UUID list converters =====
    @TypeConverter
    @NonNull
    public static String fromUuids(@Nullable Set<UUID> uuids) {
        if (uuids == null) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (UUID uuid : uuids) {
            if (uuid != null) {
                if (!stringBuilder.isEmpty()) {
                    stringBuilder.append(CSV_SEPARATOR);
                }
                stringBuilder.append(uuid);
            }
        }
        return stringBuilder.toString();
    }

    @TypeConverter
    @NonNull
    public static Set<UUID> toUuids(@Nullable String data) {
        Set<UUID> result = new HashSet<>();
        if (data == null) {
            return result;
        }
        String[] parts = data.split(String.valueOf(CSV_SEPARATOR));
        for (String part : parts) {
            try {
                result.add(UUID.fromString(part.trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

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
