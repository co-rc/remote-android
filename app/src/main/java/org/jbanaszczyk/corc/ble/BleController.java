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

import java.util.List;
import java.util.UUID;

public class BleController {

    private static final String LOG_TAG = "CORC:BleController";

    private static final UUID CORC_SERVICE_UUID = UUID.fromString("B13A1000-9F2A-4F3B-9C8E-A7D4E3C8B125");

    private final Context appContext;
    private final BleConnectionListener listener;
    private final Handler connectionHandler = new Handler(Looper.getMainLooper());
    private final BleDeviceRegistry registry = new BleDeviceRegistry();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
            var address = BleDeviceAddress.getAddressFromDevice(gatt);
            Log.d(LOG_TAG, "onConnectionStateChange(): address=" + address + ", status=" + status + ", newState=" + newState);

            registry.unmarkConnecting(address);

            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED -> {
                    if (address.isEmpty()) {
                        Log.w(LOG_TAG, "STATE_CONNECTED with invalid address – ignoring: " + address);
                        try {
                            gatt.close();
                        } catch (Exception ignored) {
                        }
                        return;
                    }
                    var device = registry
                            .ensure(address)
                            .setBluetoothGatt(gatt);

                    listener.onConnectionStateChanged(device, true);

                    var started = gatt.discoverServices();
                    Log.d(LOG_TAG, "discoverServices() started=" + started);
                }
                case BluetoothProfile.STATE_DISCONNECTED -> {
                    var device = registry
                            .ensure(address)
                            .setBluetoothGatt(null);
                    try {
                        gatt.close();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Error closing GATT for " + address, e);
                    }
                    listener.onConnectionStateChanged(device, false);
                }
                case BluetoothProfile.STATE_DISCONNECTING ->
                        Log.d(LOG_TAG, "onConnectionStateChange(): STATE_DISCONNECTING");
                case BluetoothProfile.STATE_CONNECTING -> Log.d(LOG_TAG, "onConnectionStateChange(): STATE_CONNECTING");
            }
        }

        @Override
        public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
            var address = BleDeviceAddress.getAddressFromDevice(gatt);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(LOG_TAG, "onServicesDiscovered(): GATT error " + status + " for " + address);
                listener.onScanError("Service discovery failed for " + address + " (status " + status + ")");
                return;
            }

            if (address.isEmpty()) {
                Log.w(LOG_TAG, "onServicesDiscovered(): invalid address – ignoring");
                return;
            }

            var serviceUuids = gatt
                    .getServices()
                    .stream()
                    .map(BluetoothGattService::getUuid)
                    .toList();

            Log.d(LOG_TAG, "onServicesDiscovered(): address=" + address + ", services=" + serviceUuids);

            var device = registry
                    .ensure(address)
                    .setServices(serviceUuids);

            listener.onDeviceReady(device);
        }
    };
    private boolean scanning = false;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;

    public BleController(@NonNull Context context, @NonNull BleConnectionListener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
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
    }

    public boolean isScanning() {
        return scanning;
    }

    @SuppressLint("MissingPermission")
    public boolean startScan() {
        return startScan(0L);
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
    }    private final Runnable scanTimeoutRunnable = () -> {
        Log.d(LOG_TAG, "Scan timeout reached → stopScan()");
        stopScan();
    };

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
                var gatt = device.getBluetoothGatt();
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

    private void handleScanResult(@NonNull ScanResult result) {
        BluetoothDevice device = result.getDevice();
        if (device == null) {
            return;
        }

        BleDeviceAddress address = new BleDeviceAddress(device.getAddress());
        if (BleDeviceAddress.isEmpty(address.getValue())) {
            return;
        }

        if (registry.isConnecting(address)) {
            return;
        }

        if (registry.isConnected(address)) {
            return;
        }

        Log.d(LOG_TAG, "handleScanResult(): scheduling connect to " + address);
        registry.markConnecting(address);
        connectionHandler.post(() -> connectToDevice(device));
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice(@NonNull BluetoothDevice device) {
        BleDeviceAddress address = new BleDeviceAddress(device.getAddress());
        if (BleDeviceAddress.isEmpty(address.getValue())) {
            return;
        }

        try {
            Log.d(LOG_TAG, "connectToDevice(): " + address);
            BluetoothGatt gatt = device.connectGatt(appContext, false, gattCallback);

            if (gatt == null) {
                Log.e(LOG_TAG, "connectGatt() returned null for " + address);
                registry.unmarkConnecting(address);
                listener.onScanError("Failed to connect to " + address);
            }
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Missing BLUETOOTH_CONNECT permission when connecting", e);
            registry.unmarkConnecting(address);
            listener.onScanError("Missing BLUETOOTH_CONNECT permission");
        } catch (Exception e) {
            Log.e(LOG_TAG, "connectToDevice() failed for " + address, e);
            registry.unmarkConnecting(address);
            listener.onScanError("Failed to connect to " + address + ": " + e.getMessage());
        }
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




