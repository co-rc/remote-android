package org.jbanaszczyk.corc;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import org.jbanaszczyk.corc.ble.BleController;
import org.jbanaszczyk.corc.ble.BleDevice;

import java.util.List;

public class MainActivity extends AppCompatActivity implements BleConnectionListener {

    private static final String LOG_TAG = "CORC:Main";
    private static final List<String> REQUIRED_PERMISSIONS = List.of(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
    );

    private static final long BLE_SCAN_TIMEOUT_MS = 3_000L;
    private static final long BLE_SCAN_REPEAT_MS = 5_000L;

    private BleController bleController = null;
    private ScanScheduler scanScheduler;
    private boolean bleFeaturesStarted = false;
    private boolean appInForeground = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "==[ onCreate ]========");
        scanScheduler = new ScanScheduler(this::startScanning);
        setContentView(R.layout.activity_main);
        initViews();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(LOG_TAG, "==[ onStart ]========");
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothStateReceiver, filter);
        continueStartup();
    }

    private void continueStartup() {
        if (!hasAllRequiredPermissions()) {
            requestNeededPermissions();
            return;
        }
        if (!isBluetoothEnabled()) {
            requestEnableBluetooth();
            return;
        }
        startBleFeatures();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "==[ onResume ]========");
        appInForeground = true;
        if (hasAllRequiredPermissions() && isBluetoothEnabled()) {
            startScanning();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(LOG_TAG, "==[ onPause ]========");
        appInForeground = false;
        cancelScanScheduling();
        stopScanning();
    }

    @Override
    protected void onStop() {
        Log.d(LOG_TAG, "==[ onStop ]========");
        unregisterReceiver(bluetoothStateReceiver);
        super.onStop();
    }

    // Timeout is handled inside BleController now.

    private void onBluetoothTurnedOn() {
        Toast.makeText(this, R.string.bluetooth_enabled_message, Toast.LENGTH_SHORT).show();
        updateUiForBluetooth(true);
        continueStartup();
    }

    private void onBluetoothTurnedOff() {
        Toast.makeText(this, R.string.bluetooth_disabled_message, Toast.LENGTH_SHORT).show();
        updateUiForBluetooth(false);
        stopBleFeatures();
    }

    private void updateUiForBluetooth(boolean enabled) {

        View root = findViewById(R.id.root_layout);

        float alpha = enabled ? 1.0f : 0.1f;
        root.setAlpha(alpha);

        // TODO
        //   findViewById(R.id.scanButton).setEnabled(enabled);
        //   findViewById(R.id.connectButton).setEnabled(enabled);
        //   ...
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG, "==[ onDestroy ]========");
        stopBleFeatures();
        bleController = null;
        super.onDestroy();
    }

    private boolean isBluetoothEnabled() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
        return adapter != null && adapter.isEnabled();
    }

    private void requestEnableBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        enableBluetoothLauncher.launch(enableBtIntent);
    }

    private void startBleFeatures() {
        if (bleFeaturesStarted) return;
        if (bleController == null) {
            bleController = new BleController(this, this);
        }
        bleController.initialize();
        bleFeaturesStarted = true;
    }

    private void stopBleFeatures() {
        if (!bleFeaturesStarted) return;
        Log.d(LOG_TAG, "stopBleFeatures()");
        cancelScanScheduling();
        stopScanning();
        if (bleController != null) {
            bleController.disconnectAllDevices();
            bleController.shutdown();
        }
        bleFeaturesStarted = false;
    }

    //===============================================================

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                if (hasAllRequiredPermissions()) {
                    continueStartup();
                } else {
                    showPermissionsRequiredMessage();
                }
            }
    );

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            activityResult -> {
                if (activityResult.getResultCode() == Activity.RESULT_OK) {
                    continueStartup();
                } else {
                    showBluetoothRequiredMessage();
                }
            }
    );

    //===============================================================

    private void startScanning() {
        var controllerScanning = bleController != null && bleController.isScanning();
        if (!meetsScanPreconditions() || controllerScanning) return;
        Log.d(LOG_TAG, "startScanning()");

        cancelScanScheduling();

        boolean started = bleController.startScan(BLE_SCAN_TIMEOUT_MS);
        if (!started) {
            scheduleNextScan();
        }
    }

    private void stopScanning() {
        Log.d(LOG_TAG, "stopScanning()");
        if (bleController != null) {
            bleController.stopScan();
        }
    }

    private void scheduleNextScan() {
        if (!meetsScanPreconditions()) {
            Log.d(LOG_TAG, "scheduleNextScan(): conditions not met (foreground/permissions/bluetooth/features), not scheduling");
            return;
        }
        Log.d(LOG_TAG, "scheduleNextScan(): scheduling next scan in " + BLE_SCAN_REPEAT_MS + " ms");
        if (scanScheduler != null) {
            scanScheduler.schedule(BLE_SCAN_REPEAT_MS);
        }
    }

    private void cancelScanScheduling() {
        if (scanScheduler != null) {
            scanScheduler.cancelAll();
        }
    }

    //===============================================================

    private boolean meetsScanPreconditions() {
        return appInForeground && bleFeaturesStarted && hasAllRequiredPermissions() && isBluetoothEnabled();
    }

    private void showPermissionsRequiredMessage() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.bluetooth_permissions_required_title)
                .setMessage(R.string.bluetooth_permissions_required_message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .show();
    }

    private void showBluetoothRequiredMessage() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.bluetooth_required_title)
                .setMessage(R.string.bluetooth_required_message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .show();
    }

    //===============================================================

    private void initViews() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // TODO: wire up buttons and BLE actions
    }

    //==[ BleConnectionListener ]===================

    @Override
    public void onScanStarted() {
        runOnUiThread(() -> {
            var actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setSubtitle(R.string.scan_in_progress_subtitle);
            }
        });
    }

    @Override
    public void onScanEnd(int activeConnectionsCount) {

        Log.d(LOG_TAG, "onScanEnd() " + activeConnectionsCount);

        runOnUiThread(() -> {
            var actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setSubtitle(R.string.scan_end_subtitle);
            }
        });

        cancelScanScheduling();
        if (appInForeground) {
            scheduleNextScan();
        }
    }

    @Override
    public void onScanFailed(@NonNull String message) {
        runOnUiThread(() -> {
            var actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setSubtitle(R.string.scan_failed_subtitle);
            }
            Toast
                    .makeText(this, getString(R.string.scan_failed_toast, message), Toast.LENGTH_SHORT)
                    .show();
        });

        cancelScanScheduling();
        if (appInForeground) {
            scheduleNextScan();
        }
    }

    @Override
    public void onScanError(@NonNull String message) {
        runOnUiThread(() -> {
            var actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setSubtitle(R.string.ble_error_subtitle);
            }
            Toast
                    .makeText(this, getString(R.string.ble_error_toast, message), Toast.LENGTH_LONG)
                    .show();
        });
    }

    @Override
    public void onConnectionStateChanged(@NonNull BleDevice bleDevice, boolean connected) {

    }

    @Override
    public void onDeviceReady(BleDevice bleDevice) {

    }

    //===============================================================

    private List<String> getMissingPermissions() {
        return REQUIRED_PERMISSIONS
                .stream()
                .filter(permission -> ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
                .toList();
    }

    private boolean hasAllRequiredPermissions() {
        return getMissingPermissions().isEmpty();
    }

    private void requestNeededPermissions() {
        var missing = getMissingPermissions();
        if (!missing.isEmpty()) {
            Log.d(LOG_TAG, "Missing permissions: " + missing);
            requestPermissionsLauncher.launch(missing.toArray(String[]::new));
        }
    }

    //===============================================================

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) return;
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            switch (state) {
                case BluetoothAdapter.STATE_ON -> onBluetoothTurnedOn();
                case BluetoothAdapter.STATE_OFF -> onBluetoothTurnedOff();
                // optional: STATE_TURNING_ON / STATE_TURNING_OFF
            }
        }
    };
}
