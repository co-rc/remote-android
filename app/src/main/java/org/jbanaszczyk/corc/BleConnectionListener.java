package org.jbanaszczyk.corc;

import androidx.annotation.NonNull;
import org.jbanaszczyk.corc.ble.BleDevice;
import org.jbanaszczyk.corc.ble.internal.BleDevicePersistent;

public interface BleConnectionListener {

    void onScanStarted();

    void onScanEnd(int activeConnectionsCount);

    void onScanFailed(String message);

    void onScanError(String message);

    void onConnectionStateChanged(@NonNull BleDevice bleDevice, boolean connected);

    void onDeviceReady(BleDevice bleDevice);
}
