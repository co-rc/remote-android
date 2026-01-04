package org.jbanaszczyk.corc.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.jbanaszczyk.corc.ble.internal.BleDevicePersistent;

import java.util.Set;
import java.util.UUID;

public class BleDevice {

    @NonNull
    private final BleDevicePersistent persistent;

    public BleDevice(@NonNull BleDevicePersistent persistent) {
        this.persistent = persistent;
    }

    @NonNull
    public BleDeviceAddress getAddress() {
        return persistent.getAddress();
    }

    @NonNull
    public String getConfiguration() {
        return persistent.getConfiguration();
    }

    public BleDevice setConfiguration(@Nullable String configuration) {
        persistent.setConfiguration(configuration);
        return this;
    }

    @NonNull
    public Set<UUID> getServices() {
        return persistent.getServices();
    }

    public BleDevice setServices(@Nullable Set<UUID> services) {
        persistent.setServices(services);
        return this;
    }
}
