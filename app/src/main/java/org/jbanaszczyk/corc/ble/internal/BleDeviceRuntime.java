package org.jbanaszczyk.corc.ble.internal;

import android.bluetooth.BluetoothGatt;
import androidx.annotation.Nullable;

public class BleDeviceRuntime {


    @Nullable
    private BluetoothGatt bluetoothGatt;

    public BleDeviceRuntime() {
        this.bluetoothGatt = null;
    }

    public BleDeviceRuntime(@Nullable BluetoothGatt bluetoothGatt) {
        this.bluetoothGatt = bluetoothGatt;
    }

    public boolean isConnected() {
        return bluetoothGatt != null;
    }

    @Nullable
    public BluetoothGatt getBluetoothGatt() {
        return bluetoothGatt;
    }

    public void setBluetoothGatt(@Nullable BluetoothGatt bluetoothGatt) {
        this.bluetoothGatt = bluetoothGatt;
    }
}
