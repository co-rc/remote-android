package org.jbanaszczyk.corc.ble.core;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.jbanaszczyk.corc.BleConnectionListener;
import org.jbanaszczyk.corc.ble.BleDevice;
import org.jbanaszczyk.corc.ble.BleDeviceAddress;
import org.jbanaszczyk.corc.ble.BleDeviceRegistry;
import org.jbanaszczyk.corc.ble.BleConnectionContext;
import org.jbanaszczyk.corc.ble.repo.BleDeviceRepository;

import java.util.stream.Collectors;

/**
 * Dedicated GATT client holding state machine and callbacks. Delegates queue lifecycle.
 */
public final class BleGattClient {
    private static final String LOG_TAG = "CORC:BleGattClient";

    private enum GattState { // kept for backward compatibility of API; delegates to BleConnectionContext
        DISCONNECTED,
        CONNECTING,
        SERVICES_DISCOVERING,
        READY,
        DISCONNECTING
    }

    private final Context appContext;
    private final BleDeviceRegistry registry;
    private final BleDeviceRepository deviceRepository;
    private final BleConnectionListener listener;
    private final OperationQueue operationQueue;
    private final OperationExecutor operationExecutor;

    private volatile GattState state = GattState.DISCONNECTED;

    public BleGattClient(@NonNull Context context,
                         @NonNull BleDeviceRegistry registry,
                         @NonNull BleDeviceRepository deviceRepository,
                         @NonNull BleConnectionListener listener,
                         @NonNull OperationQueue operationQueue,
                         @NonNull OperationExecutor operationExecutor) {
        this.appContext = context.getApplicationContext();
        this.registry = registry;
        this.deviceRepository = deviceRepository;
        this.listener = listener;
        this.operationQueue = operationQueue;
        this.operationExecutor = operationExecutor;
        // Provide default executor to the queue so it can run pending ops when READY
        this.operationQueue.setExecutor(operationExecutor);
    }

    public GattState getState() {
        return state;
    }

    /**
     * Enqueues a BLE operation for the provided device. Returns false if GATT is not READY
     * or device has no active GATT instance.
     */
    public boolean enqueue(@NonNull BleDevice device, @NonNull BleOperation operation) {
        var ctx = registry.getOrCreateContext(device.getAddress());
        BluetoothGatt gatt = ctx.getGatt();
        if (gatt == null) return false;
        if (ctx.getState() != BleConnectionContext.GattState.READY) return false;
        operationQueue.enqueue(operation, gatt, operationExecutor);
        return true;
    }

    @SuppressLint("MissingPermission")
    public void connect(@NonNull BleDevice device, @NonNull BluetoothDevice bluetoothDevice) {
        final BleDeviceAddress address = device.getAddress();
        Log.d(LOG_TAG, "connect(): " + address);

        try {
            BluetoothGatt gatt = bluetoothDevice.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            if (gatt == null) {
                Log.e(LOG_TAG, "connectGatt() returned null for " + address);
                listener.onScanError("Failed to connect to " + address);
                return;
            }
            var ctx = registry.getOrCreateContext(address);
            ctx.setGatt(gatt);
            ctx.setState(BleConnectionContext.GattState.CONNECTING);
            state = GattState.CONNECTING; // mirror
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Missing BLUETOOTH_CONNECT permission when connecting to " + address, e);
            listener.onScanError("Missing BLUETOOTH_CONNECT permission");
        } catch (NullPointerException ignore) {
            // Defensive – some Android stacks may throw
        } catch (Exception e) {
            Log.e(LOG_TAG, "connect() failed for " + address, e);
            listener.onScanError("Failed to connect to " + address + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    @SuppressLint("MissingPermission")
    private void safeCloseGatt(@NonNull BluetoothGatt gatt) {
        try {
            gatt.close();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error closing GATT", e);
        } finally {
            operationQueue.clear();
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
            BleDeviceAddress address = BleDeviceAddress.getAddressFromGatt(gatt);
            if (address.isEmpty()) {
                Log.w(LOG_TAG, "onServicesDiscovered(): invalid address – ignoring");
                return;
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(LOG_TAG, "onServicesDiscovered(): GATT error " + status + " for " + address);
                listener.onScanError("Service discovery failed for " + address + " (status " + status + ")");
                return;
            }

            var serviceUuids = gatt.getServices().stream().map(BluetoothGattService::getUuid).collect(Collectors.toSet());
            Log.d(LOG_TAG, "onServicesDiscovered(): address=" + address + ", services=" + serviceUuids);

            var device = registry.ensure(address);
            device.setServices(serviceUuids);
            deviceRepository.save(device);
            var ctx = registry.getOrCreateContext(address);
            ctx.setState(BleConnectionContext.GattState.READY);
            state = GattState.READY;
            operationQueue.tryExecuteNext(ctx.getGatt());
            listener.onDeviceReady(device);
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
            var address = BleDeviceAddress.getAddressFromGatt(gatt);
            Log.d(LOG_TAG, "onConnectionStateChange(): address=" + address + ", status=" + status + ", newState=" + newState);

            var device = registry.ensure(address);
            var ctx = registry.getOrCreateContext(address);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED -> {
                    ctx.setGatt(gatt);
                    ctx.setState(BleConnectionContext.GattState.SERVICES_DISCOVERING);
                    state = GattState.SERVICES_DISCOVERING;
                    listener.onConnectionStateChanged(device, true);
                    gatt.discoverServices();
                }
                case BluetoothProfile.STATE_DISCONNECTED -> {
                    ctx.setState(BleConnectionContext.GattState.DISCONNECTED);
                    safeCloseGatt(gatt);
                    state = GattState.DISCONNECTED;
                    listener.onConnectionStateChanged(device, false);
                }
                case BluetoothProfile.STATE_DISCONNECTING -> Log.d(LOG_TAG, "onConnectionStateChange(): STATE_DISCONNECTING");
                case BluetoothProfile.STATE_CONNECTING -> Log.d(LOG_TAG, "onConnectionStateChange(): STATE_CONNECTING");
            }
        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt,
                                         @NonNull BluetoothGattCharacteristic characteristic,
                                         @NonNull byte[] value,
                                         int status) {
            operationQueue.onOperationFinished();
        }

        @Override
        public void onCharacteristicWrite(@NonNull BluetoothGatt gatt,
                                          @NonNull BluetoothGattCharacteristic characteristic,
                                          int status) {
            operationQueue.onOperationFinished();
        }

        @Override
        public void onDescriptorWrite(@NonNull BluetoothGatt gatt,
                                      @NonNull BluetoothGattDescriptor descriptor,
                                      int status) {
            operationQueue.onOperationFinished();
        }

        @Override
        public void onMtuChanged(@NonNull BluetoothGatt gatt, int mtu, int status) {
            var address = BleDeviceAddress.getAddressFromGatt(gatt);
            var ctx = registry.getOrCreateContext(address);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                ctx.setMtu(mtu);
            }
            operationQueue.onOperationFinished();
        }
    };
}
