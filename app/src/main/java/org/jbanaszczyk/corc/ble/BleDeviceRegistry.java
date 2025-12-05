package org.jbanaszczyk.corc.ble;

import androidx.annotation.NonNull;
import org.jbanaszczyk.corc.ble.internal.BleDevicePersistent;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BleDeviceRegistry {

    @NonNull
    private final Map<BleDeviceAddress, BleDevice> devices = new ConcurrentHashMap<>();
    @NonNull
    private final Set<BleDeviceAddress> connectingAddresses = ConcurrentHashMap.newKeySet();

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
        connectingAddresses.clear();
    }

    public void markConnecting(@NonNull BleDeviceAddress address) {
        connectingAddresses.add(address);
    }

    public void unmarkConnecting(@NonNull BleDeviceAddress address) {
        connectingAddresses.remove(address);
    }

    public boolean isConnecting(@NonNull BleDeviceAddress address) {
        return connectingAddresses.contains(address);
    }

    public boolean isConnected(@NonNull BleDeviceAddress address) {
        var device = devices.get(address);
        if (device == null) {
            return false;
        }
        return device.isConnected();

    }
}
