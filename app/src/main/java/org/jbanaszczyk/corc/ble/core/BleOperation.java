package org.jbanaszczyk.corc.ble.core;

import androidx.annotation.Nullable;

import org.jbanaszczyk.corc.ble.BleDeviceAddress;

import java.util.concurrent.CompletableFuture;
import java.util.UUID;

/**
 * Describes a single GATT operation. Immutable.
 */
public final class BleOperation<T> {
    public enum BleOperationType {
        READ,
        WRITE,
        ENABLE_NOTIFY,
        DISABLE_NOTIFY,
        REQUEST_MTU
    }

    private final BleDeviceAddress address;
    private final BleOperationType bleOperationType;
    private final UUID characteristicUuid;
    @Nullable
    private final byte[] payload;
    @Nullable
    private final Integer mtu;
    private final CompletableFuture<T> future = new CompletableFuture<>();

    private BleOperation(BleDeviceAddress address, BleOperationType bleOperationType, @Nullable UUID uuid, @Nullable byte[] payload, @Nullable Integer mtu) {
        this.address = address;
        this.bleOperationType = bleOperationType;
        this.characteristicUuid = uuid;
        this.payload = payload;
        this.mtu = mtu;
    }

    public static BleOperation<byte[]> read(BleDeviceAddress address, UUID uuid) {
        return new BleOperation<>(address, BleOperationType.READ, uuid, null, null);
    }

    public static BleOperation<Void> write(BleDeviceAddress address, UUID uuid, byte[] payload) {
        return new BleOperation<>(address, BleOperationType.WRITE, uuid, payload, null);
    }

    public static BleOperation<Void> enableNotify(BleDeviceAddress address, UUID uuid) {
        return new BleOperation<>(address, BleOperationType.ENABLE_NOTIFY, uuid, null, null);
    }

    public static BleOperation<Void> disableNotify(BleDeviceAddress address, UUID uuid) {
        return new BleOperation<>(address, BleOperationType.DISABLE_NOTIFY, uuid, null, null);
    }

    public static BleOperation<Integer> requestMtu(BleDeviceAddress address, int mtu) {
        return new BleOperation<>(address, BleOperationType.REQUEST_MTU, null, null, mtu);
    }

    public BleDeviceAddress getAddress() {
        return address;
    }

    public BleOperationType getType() {
        return bleOperationType;
    }

    public UUID getCharacteristicUuid() {
        return characteristicUuid;
    }

    @Nullable
    public byte[] getPayload() {
        return payload;
    }

    @Nullable
    public Integer getMtu() {
        return mtu;
    }

    public CompletableFuture<T> getFuture() {
        return future;
    }

    @SuppressWarnings("unchecked")
    public void complete(@Nullable Object result) {
        try {
            future.complete((T) result);
        } catch (ClassCastException e) {
            future.completeExceptionally(e);
        }
    }

    public void completeExceptionally(Throwable throwable) {
        future.completeExceptionally(throwable);
    }
}
