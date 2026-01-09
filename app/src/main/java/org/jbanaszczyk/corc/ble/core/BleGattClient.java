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
import org.jbanaszczyk.corc.ble.core.protocol.BleCommandResponseManager;
import org.jbanaszczyk.corc.ble.core.protocol.BleOpcode;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Dedicated GATT client holding state machine and callbacks. Delegates queue lifecycle.
 */
public final class BleGattClient {
    private static final String LOG_TAG = "CORC:BleGattClient";
    public static final int GATT_WRITE_OVERHEAD = 3;
    public static final int MIN_MTU = 23;
    private static final int DEFAULT_MTU =
            BleCommandResponseManager.MAX_PAYLOAD_SIZE
            + BleCommandResponseManager.PAYLOAD_HEADER_SIZE
            + GATT_WRITE_OVERHEAD;

    private static final UUID CMD_CHAR_UUID = UUID.fromString("B13A1001-9F2A-4F3B-9C8E-A7D4E3C8B125");
    private static final UUID RSP_CHAR_UUID = UUID.fromString("B13A1002-9F2A-4F3B-9C8E-A7D4E3C8B125");

    private final Context appContext;
    private final BleDeviceRegistry registry;
    private final BleDeviceRepository deviceRepository;
    private final BleConnectionListener listener;
    private final OperationQueue operationQueue;
    private final OperationExecutor operationExecutor;
    private final BleCommandResponseManager commandResponseManager = new BleCommandResponseManager();

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

    /**
     * Enqueues a BLE operation for the provided device. Returns null if GATT is not READY
     * or device has no active GATT instance.
     */
    public <T> CompletableFuture<T> enqueue(@NonNull BleDevice device, @NonNull BleOperation<T> operation) {
        var ctx = registry.getOrCreateContext(device.getAddress());
        BluetoothGatt gatt = ctx.getGatt();
        if (gatt == null) return null;
        if (ctx.getState() != BleConnectionContext.GattState.READY) return null;

        return operationQueue.enqueue(operation, gatt, operationExecutor);
    }

    /**
     * Sends command; awaits response via command manager
     */
    public CompletableFuture<byte[]> sendCommand(@NonNull BleDevice device, UUID cmdUuid, UUID rspUuid, byte opcode, byte[] payload) {
        var request = commandResponseManager.createRequest(opcode, payload);
        CompletableFuture<byte[]> responseFuture = new CompletableFuture<>();
        
        // 1. Enqueue Write to CMD characteristic
        var writeOp = BleOperation.write(device.getAddress(), cmdUuid, request.data());
        var writeFuture = enqueue(device, writeOp);
        
        if (writeFuture == null) {
            return CompletableFuture.failedFuture(new RuntimeException("GATT not ready"));
        }

        writeFuture.handle((result, throwable) -> {
            if (throwable != null) {
                responseFuture.completeExceptionally(throwable);
            } else {
                // Write succeeded at transport level, now wait for notification
                commandResponseManager.setPendingResponse(request.requestId(), opcode, responseFuture);
            }
            return null;
        });

        return responseFuture;
    }

    @SuppressLint("MissingPermission")
    public void connect(@NonNull BleDevice device, @NonNull BluetoothDevice bluetoothDevice) {
        final BleDeviceAddress address = device.getAddress();
        Log.d(LOG_TAG, "connect(): " + address);

        // Connects to device; handles errors; updates connection context
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
        var address = BleDeviceAddress.getAddressFromGatt(gatt);
        try {
            gatt.close();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error closing GATT", e);
        } finally {
            if (!address.isEmpty()) {
                operationQueue.clear(address);
            }
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
            operationQueue.tryExecuteNext(ctx.getGatt());

            enqueue(device, BleOperation.enableNotify(address, RSP_CHAR_UUID))
                    .thenCompose(v -> sendCommand(device, CMD_CHAR_UUID, RSP_CHAR_UUID, BleOpcode.VERSION, null))
                    .thenAccept(payload -> {
                        if (payload.length >= 3) {
                            String ver = payload[0] + "." + payload[1] + "." + payload[2];
                            ctx.setVersion(ver);
                            Log.i(LOG_TAG, "Peripheral Version: " + ver);
                        }
                    })
                    .thenCompose(v -> sendCommand(device, CMD_CHAR_UUID, RSP_CHAR_UUID, BleOpcode.GET_DATA_MAX_LEN, null))
                    .thenAccept(payload -> {
                        if (payload.length >= 1) {
                            int maxLen = Byte.toUnsignedInt(payload[0]);
                            ctx.setDataMaxLen(maxLen);
                            Log.i(LOG_TAG, "Data Max Len: " + maxLen);
                        }
                    })
                    .exceptionally(t -> {
                        Log.e(LOG_TAG, "Failed to query device info", t);
                        return null;
                    });

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
                    listener.onConnectionStateChanged(device, true);
                    operationQueue.enqueue(BleOperation.requestMtu(address, DEFAULT_MTU), gatt, operationExecutor)
                            .thenRun(gatt::discoverServices)
                            .exceptionally(t -> {
                                Log.e(LOG_TAG, "MTU request failed, proceeding with service discovery", t);
                                gatt.discoverServices();
                                return null;
                            });
                }
                case BluetoothProfile.STATE_DISCONNECTED -> {
                    ctx.setState(BleConnectionContext.GattState.DISCONNECTED);
                    safeCloseGatt(gatt);
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
            if (status == BluetoothGatt.GATT_SUCCESS) {
                operationQueue.onOperationFinished(value);
            } else {
                operationQueue.onOperationFailed(new RuntimeException("GATT Read failed with status: " + status));
            }
        }

        @Override
        public void onCharacteristicWrite(@NonNull BluetoothGatt gatt,
                                          @NonNull BluetoothGattCharacteristic characteristic,
                                          int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                operationQueue.onOperationFinished(null);
            } else {
                operationQueue.onOperationFailed(new RuntimeException("GATT Write failed with status: " + status));
            }
        }

        @Override
        public void onDescriptorWrite(@NonNull BluetoothGatt gatt,
                                      @NonNull BluetoothGattDescriptor descriptor,
                                      int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                operationQueue.onOperationFinished(null);
            } else {
                operationQueue.onOperationFailed(new RuntimeException("GATT Descriptor Write failed with status: " + status));
            }
        }

        @Override
        public void onMtuChanged(@NonNull BluetoothGatt gatt, int mtu, int status) {
            var address = BleDeviceAddress.getAddressFromGatt(gatt);
            Log.d(LOG_TAG, "onMtuChanged(): address=" + address + ", mtu=" + mtu + ", status=" + status);
            var ctx = registry.getOrCreateContext(address);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                ctx.setMtu(mtu);
                operationQueue.onOperationFinished(mtu);
            } else {
                operationQueue.onOperationFailed(new RuntimeException("GATT MTU change failed with status: " + status));
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
                                            @NonNull BluetoothGattCharacteristic characteristic,
                                            @NonNull byte[] value) {
            commandResponseManager.handleNotification(value);
        }
    };
}
