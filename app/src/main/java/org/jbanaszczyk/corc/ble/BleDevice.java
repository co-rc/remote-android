package org.jbanaszczyk.corc.ble;

import android.bluetooth.BluetoothGatt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.jbanaszczyk.corc.ble.internal.BleDevicePersistent;

import java.util.List;
import java.util.UUID;

public class BleDevice {

    @NonNull
    private final BleDevicePersistent persistent;
    @NonNull
    private final BleDeviceRuntime runtime;


    private BleDevice(@NonNull BleDevicePersistent persistent, @NonNull BleDeviceRuntime runtime) {
        this.persistent = persistent;
        this.runtime = runtime;
    }

    public BleDevice(@NonNull BleDevicePersistent persistent) {
        this(persistent, new BleDeviceRuntime());
    }

    @NonNull
    public BleDeviceAddress getAddress() {
        return persistent.getAddress();
    }

    @NonNull
    public String getConfiguration() {
        return persistent.getConfiguration();
    }

    public void setConfiguration(@Nullable String configuration) {
        persistent.setConfiguration(configuration);
    }

    @NonNull
    public List<UUID> getServices() {
        return persistent.getServices();
    }

    public void setServices(@Nullable List<UUID> services) {
        persistent.setServices(services);
    }

    @Nullable
    public BluetoothGatt getGatt() {
        return runtime.getGatt();
    }

    public State getState() {
        return runtime.getState();
    }

    public void setState(State state) {
        runtime.setState(state);
    }

    public void setState(State state, BluetoothGatt gatt) {
        runtime.setState(state, gatt);
    }

    public enum State {
        NO_CHANGE,
        DISCONNECTED,
        CONNECTING,
        CONNECTED;

        public boolean isDiconnected() {
            return this == DISCONNECTED;
        }

        public boolean isActive() {
            return !isDiconnected();
        }
    }

    private static class BleDeviceRuntime {

        @Nullable
        private BluetoothGatt gatt = null;


        private void clear() {
            gatt = null;
        }

        private State state = State.DISCONNECTED;

        public BleDeviceRuntime() {
        }

        @Nullable
        public BluetoothGatt getGatt() {
            return gatt;
        }

        public State getState() {
            return state;
        }

        public void setState(State state) {
            if (state == State.NO_CHANGE) {
                return;
            }
            this.state = state;
            if (state.isDiconnected()) {
                clear();
            }
        }

        public void setState(State state, BluetoothGatt gatt) {
            setState(state);
            this.gatt = gatt;
        }
    }
}
