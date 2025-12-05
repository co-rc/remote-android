package org.jbanaszczyk.corc.ble;

import android.bluetooth.BluetoothGatt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.jbanaszczyk.corc.ble.internal.BleDevicePersistent;
import org.jbanaszczyk.corc.ble.internal.BleDeviceRuntime;

import java.util.List;
import java.util.UUID;

public class BleDevice {
    @NonNull
    private final BleDevicePersistent persistent;
    @NonNull
    private final BleDeviceRuntime runtime;

    public BleDevice(@NonNull BleDevicePersistent persistent, @NonNull BleDeviceRuntime runtime) {
        this.persistent = persistent;
        this.runtime = runtime;
    }

    public BleDevice(@NonNull BleDevicePersistent persistent) {
        this(persistent, new BleDeviceRuntime());
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
    public List<UUID> getServices() {
        return persistent.getServices();
    }

    public BleDevice setServices(@Nullable List<UUID> services) {
        persistent.setServices(services);
        return this;
    }

    public boolean isConnected() {
        return runtime.isConnected();
    }

    @Nullable
    public BluetoothGatt getBluetoothGatt() {
        return runtime.getBluetoothGatt();
    }

    public BleDevice setBluetoothGatt(@Nullable BluetoothGatt bluetoothGatt) {
        runtime.setBluetoothGatt(bluetoothGatt);
        return this;
    }
}
