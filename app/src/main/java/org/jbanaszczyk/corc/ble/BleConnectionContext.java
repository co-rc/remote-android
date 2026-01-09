package org.jbanaszczyk.corc.ble;

import android.bluetooth.BluetoothGatt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.jbanaszczyk.corc.ble.core.BleGattClient;

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
    private int mtu = BleGattClient.MIN_MTU;
    private int dataMaxLen = BleGattClient.MIN_MTU - BleGattClient.GATT_WRITE_OVERHEAD;
    @NonNull
    private String version = "unknown";
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
            mtu = BleGattClient.MIN_MTU;
            dataMaxLen = BleGattClient.MIN_MTU - BleGattClient.GATT_WRITE_OVERHEAD;
            version = "unknown";
            services = Set.of();
        }
    }

    public int getMtu() { return mtu; }

    public void setMtu(int mtu) { this.mtu = mtu; }

    public int getDataMaxLen() {
        return dataMaxLen;
    }

    public void setDataMaxLen(int dataMaxLen) {
        this.dataMaxLen = dataMaxLen;
    }

    @NonNull
    public String getVersion() {
        return version;
    }

    public void setVersion(@NonNull String version) {
        this.version = version;
    }

    @NonNull
    public Set<UUID> getServices() {
        return services;
    }

    public void setServices(@Nullable Set<UUID> services) {
        this.services = services == null ? Set.of() : Set.copyOf(services);
    }
}
