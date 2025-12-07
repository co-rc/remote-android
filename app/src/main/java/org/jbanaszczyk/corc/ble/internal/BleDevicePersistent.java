package org.jbanaszczyk.corc.ble.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.room.*;
import org.jbanaszczyk.corc.ble.BleDeviceAddress;
import org.jbanaszczyk.corc.ble.BleTypeConverters;

import java.util.Set;
import java.util.UUID;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

@RestrictTo(LIBRARY_GROUP)
@Entity(tableName = "ble_devices")
@TypeConverters({BleTypeConverters.class})
public class BleDevicePersistent {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "address")
    private final BleDeviceAddress address;

    @ColumnInfo(name = "services")
    @NonNull
    private Set<UUID> services;

    @ColumnInfo(name = "configuration", defaultValue = "")
    @NonNull
    private String configuration;

    public BleDevicePersistent(
            @Nullable BleDeviceAddress address,
            @Nullable Set<UUID> services,
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
    private static Set<UUID> normalizeServices(@Nullable Set<UUID> services) {
        return services == null
                ? Set.of()
                : Set.copyOf(services);
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

    @NonNull
    public Set<UUID> getServices() {
        return services;
    }

    public void setServices(@Nullable Set<UUID> services) {
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
