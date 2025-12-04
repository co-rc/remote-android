package org.jbanaszczyk.corc;

import android.bluetooth.BluetoothGatt;
import androidx.annotation.NonNull;
import org.jbanaszczyk.corc.ble.BleDevice;

import java.util.Map;

public interface BleConnectionListener {

    void onScanStarted();

    void onScanEnd(Map<String, BluetoothGatt> activeConnections);

    void onScanFailed(String message);

    void onScanError(String message);

    void onConnectionStateChanged(@NonNull BleDevice bleDevice, boolean connected);

    void onDeviceReady(BleDevice bleDevice);
}
