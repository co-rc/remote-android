package org.jbanaszczyk.corc.ble.repo;

import androidx.annotation.NonNull;
import org.jbanaszczyk.corc.ble.BleDevice;

import java.util.Collection;
import java.util.List;

public interface BleDeviceRepository {

    void save(@NonNull BleDevice device);

    void saveAll(@NonNull Collection<BleDevice> devices);

    @NonNull
    List<BleDevice> loadAll();
}
