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
            try {
                var mtu = operation.getMtu();
                if (mtu != null) {
                    gatt.requestMtu(mtu);
                }
            } catch (SecurityException se) {
                Log.e(LOG_TAG, "Missing BLUETOOTH_CONNECT permission during requestMtu", se);
            } catch (Exception e) {
                Log.e(LOG_TAG, "requestMtu failed: " + e.getMessage(), e);
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
            switch (operation.getType()) {
                case READ -> gatt.readCharacteristic(characteristic);
                case WRITE -> gatt.writeCharacteristic(
                        characteristic,
                        operation.getPayload(),
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                case ENABLE_NOTIFY -> enableNotify(gatt, characteristic);
                case DISABLE_NOTIFY -> disableNotify(gatt, characteristic);
                default -> {
                }
            }
        } catch (SecurityException se) {
            Log.e(LOG_TAG, "Missing BLUETOOTH_CONNECT permission during execute", se);
        } catch (Exception e) {
            Log.e(LOG_TAG, "GATT execute failed: " + e.getMessage(), e);
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
