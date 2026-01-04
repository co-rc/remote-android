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
    @NonNull
    private final Map<BleDeviceAddress, BleConnectionContext> contexts = new ConcurrentHashMap<>();

    public BleDeviceRegistry() {
    }

    @NonNull
    public Collection<BleDevice> registerPersistedDevices(@NonNull Collection<BleDevice> persistedDevices) {
        List<BleDevice> result = new ArrayList<>();
        for (BleDevice stored : persistedDevices) {
            if (stored == null) continue;
            BleDeviceAddress address = stored.getAddress();
            if (address.isEmpty()) continue;

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
        // Ensure context exists as well
        contexts.putIfAbsent(address, new BleConnectionContext());
        return race != null ? race : created;
    }

    @NonNull
    public Collection<BleDevice> all() {
        return devices.values();
    }

    public int size() {
        return devices.size();
    }

    public void clearAll() {
        devices.clear();
        contexts.clear();
    }

    @NonNull
    public BleConnectionContext getOrCreateContext(@NonNull BleDeviceAddress address) {
        ensure(address);
        BleConnectionContext ctx = contexts.get(address);
        if (ctx == null) {
            ctx = new BleConnectionContext();
            contexts.put(address, ctx);
        }
        return ctx;
    }

    public BleConnectionContext getContext(@NonNull BleDeviceAddress address) {
        return contexts.get(address);
    }

    @NonNull
    public Collection<BleConnectionContext> allContexts() { return contexts.values(); }
}
