package org.jbanaszczyk.corc.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import androidx.annotation.NonNull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jbanaszczyk.corc.BleConnectionListener;
import org.jbanaszczyk.corc.ble.core.AndroidScheduler;
import org.jbanaszczyk.corc.ble.core.BleGattClient;
import org.jbanaszczyk.corc.ble.core.BleOperation;
import org.jbanaszczyk.corc.ble.core.OperationExecutor;
import org.jbanaszczyk.corc.ble.core.OperationQueue;
import org.jbanaszczyk.corc.ble.core.StandardGattOperationExecutor;
import org.jbanaszczyk.corc.ble.repo.BleDeviceRepository;
import org.jbanaszczyk.corc.ble.repo.RoomBleDeviceRepository;

public class BleController {

    private static final String LOG_TAG = "CORC:BleController";

    private static final UUID CORC_SERVICE_UUID = UUID.fromString("B13A1000-9F2A-4F3B-9C8E-A7D4E3C8B125");

    private final Context appContext;
    private final BleConnectionListener listener;
    private final Handler connectionHandler = new Handler(Looper.getMainLooper());
    private final BleDeviceRegistry registry;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BleDeviceRepository deviceRepository;
    // ----- Operation queue infrastructure (single, minimal integration) -----
    private final OperationQueue operationQueue;
    private final OperationExecutor operationExecutor;
    private final Handler operationHandler;
    private final BleGattClient gattClient;
    private final long operationTimeoutMillis = TimeUnit.SECONDS.toMillis(10); // default timeout per operation
    private boolean scanning = false;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;

    public BleController(@NonNull Context context, @NonNull BleConnectionListener listener) {
        this(context, listener, new RoomBleDeviceRepository(context));
    }

    public BleController(@NonNull Context context,
                         @NonNull BleConnectionListener listener,
                         @NonNull BleDeviceRepository deviceRepository) {
        this(context, listener, deviceRepository, new Handler(Looper.getMainLooper()), new StandardGattOperationExecutor());
    }

    public BleController(@NonNull Context context,
                         @NonNull BleConnectionListener listener,
                         @NonNull BleDeviceRepository deviceRepository,
                         @NonNull Handler operationHandler,
                         @NonNull OperationExecutor operationExecutor) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        this.registry = new BleDeviceRegistry();
        this.deviceRepository = deviceRepository;
        this.operationHandler = operationHandler;
        this.operationExecutor = operationExecutor;
        this.operationQueue = new OperationQueue(new AndroidScheduler(operationHandler), () -> operationTimeoutMillis);
        this.gattClient = new BleGattClient(appContext, registry, deviceRepository, listener, operationQueue, operationExecutor);
    }

    // ---- High level GATT convenience (delegates to queue) ----
    public boolean readCharacteristic(@NonNull BleDevice device, @NonNull UUID characteristicUuid) {
        return gattClient.enqueue(device, BleOperation.read(characteristicUuid));
    }

    public boolean writeCharacteristic(@NonNull BleDevice device, @NonNull UUID characteristicUuid, @NonNull byte[] payload) {
        return gattClient.enqueue(device, BleOperation.write(characteristicUuid, payload));
    }

    public boolean enableNotifications(@NonNull BleDevice device, @NonNull UUID characteristicUuid) {
        return gattClient.enqueue(device, BleOperation.enableNotify(characteristicUuid));
    }

    public boolean disableNotifications(@NonNull BleDevice device, @NonNull UUID characteristicUuid) {
        return gattClient.enqueue(device, BleOperation.disableNotify(characteristicUuid));
    }

    public void initialize() {
        Log.d(LOG_TAG, "initialize()");

        bluetoothManager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Log.e(LOG_TAG, "BluetoothManager is null");
            listener.onScanError("BluetoothManager is not available");
            return;
        }

        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e(LOG_TAG, "BluetoothAdapter is null");
            listener.onScanError("BluetoothAdapter is not available");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.w(LOG_TAG, "Bluetooth is disabled in initialize()");
            listener.onScanError("Bluetooth is disabled");
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Log.e(LOG_TAG, "BluetoothLeScanner is null");
            listener.onScanError("BluetoothLeScanner is not available");
        }

        startReconnectToPersistedDevices();
    }

    public boolean isScanning() {
        return scanning;
    }

    @SuppressLint("MissingPermission")
    public void startScan() {
        startScan(0L);
    }

    @SuppressLint("MissingPermission")
    public boolean startScan(long timeoutMs) {
        Log.d(LOG_TAG, "startScan(timeoutMs=" + timeoutMs + ")");

        if (bluetoothAdapter == null || bluetoothLeScanner == null) {
            Log.e(LOG_TAG, "Cannot start scan – adapter or scanner is null");
            return false;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.w(LOG_TAG, "Bluetooth is disabled in startScan()");
            return false;
        }

        if (scanning) {
            Log.d(LOG_TAG, "startScan(): already scanning → noop");
            return false;
        }

        try {
            var filters = List.of(new ScanFilter
                    .Builder()
                    .setServiceUuid(new ParcelUuid(CORC_SERVICE_UUID))
                    .build()
            );
            var settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                    .setReportDelay(0L)
                    .build();

            bluetoothLeScanner.startScan(filters, settings, scanCallback);
            listener.onScanStarted();
            scanning = true;
            mainHandler.removeCallbacks(scanTimeoutRunnable);
            if (timeoutMs > 0L) {
                mainHandler.postDelayed(scanTimeoutRunnable, timeoutMs);
            }
            return true;
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Missing BLUETOOTH_SCAN permission at runtime", e);
            return false;
        } catch (Exception e) {
            Log.e(LOG_TAG, "startScan() failed", e);
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    public void stopScan() {
        Log.d(LOG_TAG, "stopScan()");

        if (bluetoothLeScanner == null) return;

        try {
            // Cancel any pending timeout
            mainHandler.removeCallbacks(scanTimeoutRunnable);
            if (!scanning) {
                bluetoothLeScanner.stopScan(scanCallback);
                return;
            }

            bluetoothLeScanner.stopScan(scanCallback);
            scanning = false;
            listener.onScanEnd(registry.size());
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Missing BLUETOOTH_SCAN permission at runtime", e);
        } catch (Exception e) {
            Log.e(LOG_TAG, "stopScan() failed", e);
        }
    }

    @SuppressLint("MissingPermission")
    public void disconnectAllDevices() {
        Log.d(LOG_TAG, "disconnectAllDevices()");

        for (BleDevice device : registry.all()) {
            try {
                var ctx = registry.getContext(device.getAddress());
                var gatt = ctx != null ? ctx.getGatt() : null;
                if (gatt != null) {
                    gatt.disconnect();
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error while disconnecting GATT", e);
            }
        }

        registry.clearAll();
    }

    public void shutdown() {
        Log.d(LOG_TAG, "shutdown()");
        stopScan();
        disconnectAllDevices();
        connectionHandler.removeCallbacksAndMessages(null);
        bluetoothLeScanner = null;
        bluetoothAdapter = null;
        bluetoothManager = null;
    }

    private final Runnable scanTimeoutRunnable = () -> {
        Log.d(LOG_TAG, "Scan timeout reached → stopScan()");
        stopScan();
    };

    private void handleScanResult(@NonNull ScanResult result) {
        BluetoothDevice bluetoothDevice = result.getDevice();
        if (bluetoothDevice == null) return;

        BleDeviceAddress address = new BleDeviceAddress(bluetoothDevice.getAddress());
        if (BleDeviceAddress.isEmpty(address.getValue())) return;

        var device = registry.ensure(address);
        var ctx = registry.getOrCreateContext(address);
        if (ctx.getState() != BleConnectionContext.GattState.DISCONNECTED) return;

        Log.d(LOG_TAG, "handleScanResult(): scheduling connect to " + address);
        ctx.setState(BleConnectionContext.GattState.CONNECTING);
        connectionHandler.post(() -> connectToDevice(device, bluetoothDevice));
    }

    private void startReconnectToPersistedDevices() {
        new Thread(() -> {
            try {
                if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                    Log.w(LOG_TAG, "Skipping persisted reconnect – adapter not ready or disabled");
                    return;
                }

                List<BleDevice> persisted = deviceRepository.loadAll();
                Log.d(LOG_TAG, "Attempting reconnect for " + persisted.size() + " persisted devices");

                var devicesToConnect = registry.registerPersistedDevices(persisted);

                for (BleDevice device : devicesToConnect) {
                    BleDeviceAddress address = device.getAddress();
                    try {
                        BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(address.getValue());
                        if (bluetoothDevice == null) {
                            Log.w(LOG_TAG, "getRemoteDevice returned null for " + address);
                            continue;
                        }

                        registry.getOrCreateContext(address).setState(BleConnectionContext.GattState.CONNECTING);
                        connectionHandler.post(() -> connectToDevice(device, bluetoothDevice));
                    } catch (IllegalArgumentException exception) {
                        Log.e(LOG_TAG, "Invalid Bluetooth address: " + address, exception);
                    } catch (Exception exception) {
                        Log.e(LOG_TAG, "Failed scheduling reconnect for " + address, exception);
                    }
                }

                mainHandler.post(() -> {
                    if (!scanning) {
                        startScan();
                    }
                });
            } catch (Throwable t) {
                Log.e(LOG_TAG, "Persisted devices reconnect routine failed", t);
            }
        }, "corc-ble-reconnect").start();
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice(@NonNull BleDevice device, BluetoothDevice bluetoothDevice) {
        gattClient.connect(device, bluetoothDevice);
    }

    private void handleConnectionFailure(@NonNull BleDevice device, @NonNull String message) {
        registry.getOrCreateContext(device.getAddress()).setState(BleConnectionContext.GattState.DISCONNECTED);
        listener.onScanError(message);
    }

    private final ScanCallback scanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, @NonNull ScanResult result) {
            handleScanResult(result);
        }

        @Override
        public void onBatchScanResults(@NonNull List<ScanResult> results) {
            for (ScanResult result : results) {
                handleScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(LOG_TAG, "onScanFailed(), errorCode=" + errorCode);
            scanning = false;
            mainHandler.removeCallbacks(scanTimeoutRunnable);
            listener.onScanFailed("Scan failed with error code: " + errorCode);
        }
    };


    // ======== Minimal BLE operation queue with per-operation timeout ========
}




