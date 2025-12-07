package org.jbanaszczyk.corc.ble;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import androidx.annotation.NonNull;
import org.jbanaszczyk.corc.BleConnectionListener;
import org.jbanaszczyk.corc.ble.repo.BleDeviceRepository;
import org.jbanaszczyk.corc.ble.repo.RoomBleDeviceRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BleController {

    private static final String LOG_TAG = "CORC:BleController";

    private static final UUID CORC_SERVICE_UUID = UUID.fromString("B13A1000-9F2A-4F3B-9C8E-A7D4E3C8B125");

    private final Context appContext;
    private final BleConnectionListener listener;
    private final Handler connectionHandler = new Handler(Looper.getMainLooper());
    private final BleDeviceRegistry registry;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BleDeviceRepository deviceRepository;
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
            var address = BleDeviceAddress.getAddressFromGatt(gatt);

            if (address.isEmpty()) {
                Log.w(LOG_TAG, "onServicesDiscovered(): invalid address – ignoring");
                return;
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(LOG_TAG, "onServicesDiscovered(): GATT error " + status + " for " + address);
                listener.onScanError("Service discovery failed for " + address + " (status " + status + ")");
                return;
            }

            var serviceUuids = gatt
                    .getServices()
                    .stream()
                    .map(BluetoothGattService::getUuid)
                    .collect(Collectors.toSet());

            Log.d(LOG_TAG, "onServicesDiscovered(): address=" + address + ", services=" + serviceUuids);

            var device = registry.ensure(address);
            device.setServices(serviceUuids);
            // Persist the completed device (overwrite by address) using repository (off main thread)
            deviceRepository.save(device);
            listener.onDeviceReady(device);
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
            var address = BleDeviceAddress.getAddressFromGatt(gatt);
            Log.d(LOG_TAG, "onConnectionStateChange(): address=" + address + ", status=" + status + ", newState=" + newState);

            var device = registry.ensure(address);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED -> {
                    device.setState(BleDevice.State.CONNECTED, gatt);
                    listener.onConnectionStateChanged(device, true);
                    gatt.discoverServices();
                }
                case BluetoothProfile.STATE_DISCONNECTED -> {
                    device.setState(BleDevice.State.DISCONNECTED);
                    try {
                        gatt.close();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Error closing GATT for " + address, e);
                    }
                    listener.onConnectionStateChanged(device, false);
                }
                case BluetoothProfile.STATE_DISCONNECTING -> Log.d(LOG_TAG, "onConnectionStateChange(): STATE_DISCONNECTING");
                case BluetoothProfile.STATE_CONNECTING -> Log.d(LOG_TAG, "onConnectionStateChange(): STATE_CONNECTING");
            }
        }
    };
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
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        this.registry = new BleDeviceRegistry();
        this.deviceRepository = deviceRepository;
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

        if (bluetoothLeScanner == null) {
            return;
        }

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
                var gatt = device.getGatt();
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
        if (bluetoothDevice == null) {
            return;
        }

        BleDeviceAddress address = new BleDeviceAddress(bluetoothDevice.getAddress());
        if (BleDeviceAddress.isEmpty(address.getValue())) {
            return;
        }

        var device = registry.ensure(address);
        if (device.getState().isActive()) {
            return;
        }

        Log.d(LOG_TAG, "handleScanResult(): scheduling connect to " + address);

        device.setState(BleDevice.State.CONNECTING);
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

                        device.setState(BleDevice.State.CONNECTING);
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
        final BleDeviceAddress address = device.getAddress();
        Log.d(LOG_TAG, "connectToDevice(): " + address);

        final BluetoothGatt gatt;
        try {
            gatt = bluetoothDevice.connectGatt(appContext, false, gattCallback);

            if (gatt == null) {
                Log.e(LOG_TAG, "connectGatt() returned null for " + address);
                handleConnectionFailure(device, "Failed to connect to " + address);
                return;
            }
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Missing BLUETOOTH_CONNECT permission when connecting to " + address, e);
            handleConnectionFailure(device, "Missing BLUETOOTH_CONNECT permission");
            return;
        } catch (NullPointerException unexpected) {
            return;
        } catch (Exception e) {
            Log.e(LOG_TAG, "connectToDevice() failed for " + address, e);
            handleConnectionFailure(device, "Failed to connect to " + address + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            return;
        }

        device.setState(BleDevice.State.CONNECTED, gatt);
    }

    private void handleConnectionFailure(@NonNull BleDevice device, @NonNull String message) {
        device.setState(BleDevice.State.DISCONNECTED);
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


}




