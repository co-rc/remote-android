package org.jbanaszczyk.corc.db;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import org.jbanaszczyk.corc.ble.internal.BleDevicePersistent;

import java.util.List;

@Dao
public abstract class BleDeviceDao {

    private static final String LOG_TAG = "CORC:DB";

    // ===== Implementation methods used by Room (annotated) =====
    @Query("SELECT * FROM ble_devices")
    @NonNull
    protected abstract List<BleDevicePersistent> getAllImpl();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void upsertImpl(BleDevicePersistent device);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void upsertAllImpl(List<BleDevicePersistent> devices);

    @Delete
    protected abstract void deleteImpl(BleDevicePersistent device);

    @Query("DELETE FROM ble_devices")
    protected abstract void deleteAllImpl();

    // ===== Public API with logging wrappers =====
    @NonNull
    public List<BleDevicePersistent> getAll() {
        List<BleDevicePersistent> result = getAllImpl();
        int count = result.size();
        Log.d(LOG_TAG, "BleDeviceDao.getAll -> " + count + " devices");
        return result;
    }

    public void upsert(@NonNull BleDevicePersistent device) {
        String address = safeAddress(device);
        Log.d(LOG_TAG, "BleDeviceDao.upsert address=" + address);
        upsertImpl(device);
    }

    public void upsertAll(@Nullable List<BleDevicePersistent> devices) {
        int count = devices == null ? 0 : devices.size();
        Log.d(LOG_TAG, "BleDeviceDao.upsertAll count=" + count);
        if (devices == null || devices.isEmpty()) {
            return;
        }
        upsertAllImpl(devices);
    }

    public void delete(@NonNull BleDevicePersistent device) {
        String address = safeAddress(device);
        Log.d(LOG_TAG, "BleDeviceDao.delete address=" + address);
        deleteImpl(device);
    }

    public void deleteAll() {
        Log.d(LOG_TAG, "BleDeviceDao.deleteAll");
        deleteAllImpl();
    }

    @NonNull
    private static String safeAddress(@Nullable BleDevicePersistent device) {
        try {
            if (device == null) return "<null>";
            return device.getAddress().getValue();
        } catch (Throwable t) {
            return "<error>";
        }
    }
}
