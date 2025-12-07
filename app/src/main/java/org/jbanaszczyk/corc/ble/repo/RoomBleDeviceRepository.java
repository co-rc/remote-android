package org.jbanaszczyk.corc.ble.repo;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.jbanaszczyk.corc.ble.BleDevice;
import org.jbanaszczyk.corc.ble.internal.BleDevicePersistent;
import org.jbanaszczyk.corc.db.CorcDatabase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RoomBleDeviceRepository implements BleDeviceRepository {

    private static final String LOG_TAG = "CORC:BleRepo";

    @NonNull
    private final Context appContext;
    @NonNull
    private final ExecutorService ioExecutor;

    public RoomBleDeviceRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "corc-db-exec"));
    }

    @Override
    public void save(@NonNull BleDevice device) {
        Objects.requireNonNull(device, "device");
        ioExecutor.execute(() -> {
            try {
                var entity = toEntity(device);
                CorcDatabase
                        .getInstance(appContext)
                        .bleDeviceDao()
                        .upsert(entity);
            } catch (Throwable t) {
                Log.e(LOG_TAG, "Failed to persist device " + device.getAddress(), t);
            }
        });
    }

    @Override
    public void saveAll(@NonNull Collection<BleDevice> devices) {
        Objects.requireNonNull(devices, "devices");
        if (devices.isEmpty()) {
            return;
        }
        ioExecutor.execute(() -> {
            try {
                List<BleDevicePersistent> entities = new ArrayList<>(devices.size());
                for (BleDevice device : devices) {
                    if (device == null) continue;
                    entities.add(toEntity(device));
                }
                if (entities.isEmpty()) return;
                CorcDatabase
                        .getInstance(appContext)
                        .bleDeviceDao()
                        .upsertAll(entities);
            } catch (Throwable t) {
                Log.e(LOG_TAG, "Failed to persist devices batch (" + devices.size() + ")", t);
            }
        });
    }

    @NonNull
    @Override
    public List<BleDevice> loadAll() {
        try {
            List<BleDevicePersistent> entities = CorcDatabase
                    .getInstance(appContext)
                    .bleDeviceDao()
                    .getAll();
            List<BleDevice> devices = new ArrayList<>(entities.size());
            for (BleDevicePersistent entity : entities) {
                if (entity == null) continue;
                devices.add(fromEntity(entity));
            }
            Log.d(LOG_TAG, "Loaded " + devices.size() + " devices from DB");
            return devices;
        } catch (Throwable t) {
            Log.e(LOG_TAG, "Failed to load devices from DB", t);
            return List.of();
        }
    }

    @NonNull
    private static BleDevicePersistent toEntity(@NonNull BleDevice device) {
        return new BleDevicePersistent(
                device.getAddress(),
                device.getServices(),
                device.getConfiguration()
        );
    }

    @NonNull
    private static BleDevice fromEntity(@NonNull BleDevicePersistent entity) {
        return new BleDevice(entity);
    }
}
