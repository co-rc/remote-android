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
        var result = getAllImpl();
        Log.d(LOG_TAG, "BleDeviceDao.getAll -> " + result.size() + " devices");
        return result;
    }

    public void upsert(@NonNull BleDevicePersistent device) {
        Log.d(LOG_TAG, "BleDeviceDao.upsert address=" + device.getAddress().getValue());
        upsertImpl(device);
    }

    public void upsertAll(@Nullable List<BleDevicePersistent> devices) {
        if (devices == null ) return;
        Log.d(LOG_TAG, "BleDeviceDao.upsertAll count=" + devices.size());
        upsertAllImpl(devices);
    }

    public void delete(@NonNull BleDevicePersistent device) {
        Log.d(LOG_TAG, "BleDeviceDao.delete address=" + device.getAddress().getValue());
        deleteImpl(device);
    }

    public void deleteAll() {
        Log.d(LOG_TAG, "BleDeviceDao.deleteAll");
        deleteAllImpl();
    }
}
