package org.jbanaszczyk.corc.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.TypeConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BleTypeConverters {

    private static final char CSV_SEPARATOR = ',';

    // ===== UUID list converters =====
    @TypeConverter
    @NonNull
    public static String fromUuidList(@Nullable List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (UUID uuid : uuids) {
            if (uuid != null) {
                if (!sb.isEmpty()) sb.append(CSV_SEPARATOR);
                sb.append(uuid);
            }
        }
        return sb.toString();
    }

    @TypeConverter
    @NonNull
    public static List<UUID> toUuidList(@Nullable String data) {
        List<UUID> result = new ArrayList<>();
        if (data == null || data.isEmpty()) {
            return result;
        }
        String[] parts = data.split(String.valueOf(CSV_SEPARATOR));
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                result.add(UUID.fromString(trimmed));
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
