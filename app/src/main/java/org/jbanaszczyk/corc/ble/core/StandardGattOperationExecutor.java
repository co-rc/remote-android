package org.jbanaszczyk.corc.ble.core;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.util.Log;
import androidx.annotation.NonNull;
import java.util.UUID;

/**
 * Default executor that performs standard GATT operations on the provided BluetoothGatt.
 */
public final class StandardGattOperationExecutor implements OperationExecutor {
    private static final String LOG_TAG = "CORC:GattExec";
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    @SuppressLint("MissingPermission")
    @Override
    public void execute(@NonNull BluetoothGatt gatt, @NonNull BleOperation<?> operation) {
        if (operation.getType() == BleOperation.BleOperationType.REQUEST_MTU) {
            var mtu = operation.getMtu();
            if (mtu != null) {
                Log.d(LOG_TAG, "requestMtu(" + mtu + ")");
                boolean ok = gatt.requestMtu(mtu);
                if (!ok) {
                    throw new RuntimeException("gatt.requestMtu() returned false");
                }
            }
            return;
        }

        BluetoothGattCharacteristic characteristic = findCharacteristic(gatt, operation.getCharacteristicUuid());
        if (characteristic == null) {
            Log.w(LOG_TAG, "Characteristic not found: " + operation.getCharacteristicUuid());
            // OperationQueue timeout will handle it if we don't finish
            return;
        }

        try {
            boolean ok = false;
            switch (operation.getType()) {
                case READ -> ok = gatt.readCharacteristic(characteristic);
                case WRITE -> {
                    // Check if we use the new API or old. 
                    // Based on previous code, we used the 3-arg version.
                    // If the compiler complained about 'int' vs 'boolean' for readCharacteristic, 
                    // let's see what happens here.
                    int status = gatt.writeCharacteristic(
                            characteristic,
                            operation.getPayload(),
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    ok = (status == BluetoothGatt.GATT_SUCCESS);
                }
                case ENABLE_NOTIFY -> {
                    enableNotify(gatt, characteristic);
                    ok = true;
                }
                case DISABLE_NOTIFY -> {
                    disableNotify(gatt, characteristic);
                    ok = true;
                }
                default -> {
                }
            }
            if (!ok) {
                throw new RuntimeException("GATT operation " + operation.getType() + " failed");
            }
        } catch (SecurityException se) {
            Log.e(LOG_TAG, "Missing BLUETOOTH_CONNECT permission during execute", se);
            throw se;
        } catch (Exception e) {
            Log.e(LOG_TAG, "GATT execute failed: " + e.getMessage(), e);
            throw e;
        }
    }

    private static BluetoothGattCharacteristic findCharacteristic(BluetoothGatt gatt, UUID uuid) {
        for (BluetoothGattService service : gatt.getServices()) {
            BluetoothGattCharacteristic c = service.getCharacteristic(uuid);
            if (c != null) return c;
        }
        return null;
    }

    @SuppressLint("MissingPermission")
    private static void enableNotify(BluetoothGatt gatt, BluetoothGattCharacteristic ch) {
        boolean ok = gatt.setCharacteristicNotification(ch, true);
        BluetoothGattDescriptor ccc = ch.getDescriptor(CCCD_UUID);
        if (ccc != null) {
            gatt.writeDescriptor(ccc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else if (!ok) {
            Log.w(LOG_TAG, "setCharacteristicNotification failed and CCCD missing for enable");
        }
    }

    @SuppressLint("MissingPermission")
    private static void disableNotify(BluetoothGatt gatt, BluetoothGattCharacteristic ch) {
        boolean ok = gatt.setCharacteristicNotification(ch, false);
        BluetoothGattDescriptor ccc = ch.getDescriptor(CCCD_UUID);
        if (ccc != null) {
            gatt.writeDescriptor(ccc, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        } else if (!ok) {
            Log.w(LOG_TAG, "setCharacteristicNotification failed and CCCD missing for disable");
        }
    }
}
