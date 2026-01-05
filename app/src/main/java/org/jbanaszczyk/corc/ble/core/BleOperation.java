package org.jbanaszczyk.corc.ble.core;

import androidx.annotation.Nullable;

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

    private final BleOperationType bleOperationType;
    private final UUID characteristicUuid;
    @Nullable
    private final byte[] payload;
    @Nullable
    private final Integer mtu;
    private final CompletableFuture<T> future = new CompletableFuture<>();

    private BleOperation(BleOperationType bleOperationType, @Nullable UUID uuid, @Nullable byte[] payload, @Nullable Integer mtu) {
        this.bleOperationType = bleOperationType;
        this.characteristicUuid = uuid;
        this.payload = payload;
        this.mtu = mtu;
    }

    public static BleOperation<byte[]> read(UUID uuid) {
        return new BleOperation<>(BleOperationType.READ, uuid, null, null);
    }

    public static BleOperation<Void> write(UUID uuid, byte[] payload) {
        return new BleOperation<>(BleOperationType.WRITE, uuid, payload, null);
    }

    public static BleOperation<Void> enableNotify(UUID uuid) {
        return new BleOperation<>(BleOperationType.ENABLE_NOTIFY, uuid, null, null);
    }

    public static BleOperation<Void> disableNotify(UUID uuid) {
        return new BleOperation<>(BleOperationType.DISABLE_NOTIFY, uuid, null, null);
    }

    public static BleOperation<Integer> requestMtu(int mtu) {
        return new BleOperation<>(BleOperationType.REQUEST_MTU, null, null, mtu);
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
