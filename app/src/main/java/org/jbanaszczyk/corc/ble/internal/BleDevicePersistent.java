package org.jbanaszczyk.corc.ble.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;
import androidx.room.TypeConverters;
import org.jbanaszczyk.corc.ble.BleDeviceAddress;
import org.jbanaszczyk.corc.ble.BleTypeConverters;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/*
import androidx.annotation.RestrictTo;
import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;
@RestrictTo(LIBRARY_GROUP)
*/

@Entity(tableName = "ble_devices")
@TypeConverters({BleTypeConverters.class})
public class BleDevicePersistent {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "address")
    private BleDeviceAddress address;

    @ColumnInfo(name = "services")
    @NonNull
    private List<UUID> services;

    @ColumnInfo(name = "configuration", defaultValue = "")
    @NonNull
    private String configuration;

    public BleDevicePersistent(
            @Nullable BleDeviceAddress address,
            @Nullable List<UUID> services,
            @Nullable String configuration
    ) {
        this.address = normalizeAddress(address);
        this.services = normalizeServices(services);
        this.configuration = normalizeConfiguration(configuration);
    }

    @Ignore
    public BleDevicePersistent(@Nullable BleDeviceAddress address) {
        this(address, null, null);
    }

    @NonNull
    private static BleDeviceAddress normalizeAddress(@Nullable BleDeviceAddress address) {
        return new BleDeviceAddress(address);
    }

    @NonNull
    private static List<UUID> normalizeServices(@Nullable List<UUID> services) {
        return services == null
                ? Collections.emptyList()
                : List.copyOf(services);
    }

    @NonNull
    private static String normalizeConfiguration(@Nullable String configuration) {
        return configuration == null
                ? ""
                : configuration;
    }

    @NonNull
    public BleDeviceAddress getAddress() {
        return address;
    }

    public void setAddress(@Nullable BleDeviceAddress address) {
        this.address = normalizeAddress(address);
    }

    @NonNull
    public List<UUID> getServices() {
        return services;
    }

    public void setServices(@Nullable List<UUID> services) {
        this.services = normalizeServices(services);
    }

    @NonNull
    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(@Nullable String configuration) {
        this.configuration = normalizeConfiguration(configuration);
    }
}
