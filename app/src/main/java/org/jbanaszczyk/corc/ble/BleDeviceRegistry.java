package org.jbanaszczyk.corc.ble;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.jbanaszczyk.corc.ble.internal.BleDevicePersistent;
import org.jbanaszczyk.corc.db.CorcDatabase;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BleDeviceRegistry {

    private static final String LOG_TAG = "CORC:Registry";

    @NonNull
    private final Map<BleDeviceAddress, BleDevice> devices = new ConcurrentHashMap<>();
    @NonNull
    private final Set<BleDeviceAddress> connectingAddresses = ConcurrentHashMap.newKeySet();

    public BleDeviceRegistry() {
    }

    public BleDeviceRegistry(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> preloadFromDatabase(appContext), "corc-registry-preload").start();
    }

    private void preloadFromDatabase(@NonNull Context context) {
        try {
            List<BleDevicePersistent> store = CorcDatabase
                    .getInstance(context)
                    .bleDeviceDao()
                    .getAll();

            int count = 0;
            for (BleDevicePersistent bleDevicePersistent : store) {
                BleDevice device = new BleDevice(bleDevicePersistent);
                BleDeviceAddress address = device.getAddress();
                devices.put(address, device);
                count++;
            }
            Log.i(LOG_TAG, "BleDeviceRegistry preload completed: " + count + " devices loaded.");
        } catch (Throwable t) {
            Log.e(LOG_TAG, "BleDeviceRegistry preload failed", t);
        }
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
