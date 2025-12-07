package org.jbanaszczyk.corc.ble;

import androidx.annotation.NonNull;
import org.jbanaszczyk.corc.ble.internal.BleDevicePersistent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BleDeviceRegistry {

    @NonNull
    private final Map<BleDeviceAddress, BleDevice> devices = new ConcurrentHashMap<>();

    public BleDeviceRegistry() {
    }

    @NonNull
    public Collection<BleDevice> registerPersistedDevices(@NonNull Collection<BleDevice> persistedDevices) {
        List<BleDevice> result = new ArrayList<>();
        for (BleDevice stored : persistedDevices) {
            if (stored == null) {
                continue;
            }
            BleDeviceAddress address = stored.getAddress();
            if (address.isEmpty()) {
                continue;
            }

            result.add(ensure(address)
                    .setServices(stored.getServices())
                    .setConfiguration(stored.getConfiguration()));
        }
        return result;
    }

    @NonNull
    public BleDevice ensure(@NonNull BleDeviceAddress address) {
        BleDevice existing = devices.get(address);
        if (existing != null) {
            return existing;
        }
        BleDevice created = new BleDevice(new BleDevicePersistent(address));
        BleDevice race = devices.putIfAbsent(address, created);
        return race != null ? race : created;
    }

    public Collection<BleDevice> all() {
        return devices.values();
    }

    public int size() {
        return devices.size();
    }

    public void clearAll() {
        devices.clear();
    }
}
