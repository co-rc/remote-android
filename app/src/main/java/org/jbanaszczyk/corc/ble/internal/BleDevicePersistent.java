package org.jbanaszczyk.corc.ble.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.room.*;
import org.jbanaszczyk.corc.ble.BleDeviceAddress;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

@RestrictTo(LIBRARY_GROUP)
@Entity(tableName = "ble_devices")
public class BleDevicePersistent {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "address")
    private final BleDeviceAddress address;

    @ColumnInfo(name = "configuration", defaultValue = "")
    @NonNull
    private String configuration;

    public BleDevicePersistent(
            @Nullable BleDeviceAddress address,
            @Nullable String configuration
    ) {
        this.address = normalizeAddress(address);
        this.configuration = normalizeConfiguration(configuration);
    }

    @Ignore
    public BleDevicePersistent(@Nullable BleDeviceAddress address) {
        this(address, null);
    }

    @NonNull
    private static BleDeviceAddress normalizeAddress(@Nullable BleDeviceAddress address) {
        return new BleDeviceAddress(address);
    }

    @NonNull
    public BleDeviceAddress getAddress() {
        return address;
    }

    @NonNull
    private static String normalizeConfiguration(@Nullable String configuration) {
        return configuration == null
                ? ""
                : configuration;
    }

    @NonNull
    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(@Nullable String configuration) {
        this.configuration = normalizeConfiguration(configuration);
    }
}
