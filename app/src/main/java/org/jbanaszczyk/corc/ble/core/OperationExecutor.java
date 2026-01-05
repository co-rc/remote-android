package org.jbanaszczyk.corc.ble.core;

import android.bluetooth.BluetoothGatt;
import android.util.Log;
import androidx.annotation.NonNull;

public interface OperationExecutor {
    void execute(@NonNull BluetoothGatt gatt, @NonNull BleOperation<?> operation);

    static OperationExecutor logOnly() {
        return (g, op) -> Log.w("CORC:BleController",
                "OperationExecutor not set. Ignoring op " + op.getType() + " for " + op.getCharacteristicUuid());
    }
}
