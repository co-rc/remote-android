package org.jbanaszczyk.corc.ble;

import android.bluetooth.BluetoothGatt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Runtime connection context for a BLE device. Holds BluetoothGatt instance,
 * connection state and negotiated MTU. Keeps runtime separate from persistent device data.
 */
public final class BleConnectionContext {

    public enum GattState {
        DISCONNECTED,
        CONNECTING,
        SERVICES_DISCOVERING,
        READY,
        
        DISCONNECTING
    }

    @Nullable
    private BluetoothGatt gatt;
    @NonNull
    private GattState state = GattState.DISCONNECTED;
    private int mtu = 23;
    @NonNull
    private Set<UUID> services = Set.of();

    @Nullable
    public BluetoothGatt getGatt() { return gatt; }

    public void setGatt(@Nullable BluetoothGatt gatt) { this.gatt = gatt; }

    @NonNull
    public GattState getState() { return state; }

    public void setState(@NonNull GattState state) {
        this.state = state;
        if (state == GattState.DISCONNECTED) {
            gatt = null;
            mtu = 23;
            services = Set.of();
        }
    }

    public int getMtu() { return mtu; }

    public void setMtu(int mtu) { this.mtu = mtu; }

    @NonNull
    public Set<UUID> getServices() {
        return services;
    }

    public void setServices(@Nullable Set<UUID> services) {
        this.services = services == null ? Set.of() : Set.copyOf(services);
    }
}
