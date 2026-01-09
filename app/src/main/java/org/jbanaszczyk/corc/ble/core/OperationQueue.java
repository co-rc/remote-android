package org.jbanaszczyk.corc.ble.core;

import android.bluetooth.BluetoothGatt;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jbanaszczyk.corc.ble.BleDeviceAddress;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single-threaded BLE operation queue with per-operation timeout.
 */
public final class OperationQueue {
    public static final int QUEUE_CAPACITY = 64;
    private static final String LOG_TAG = "CORC:OpQueue";

    private record EnqueuedOperation(BleOperation<?> operation, BluetoothGatt gatt,
                                     OperationExecutor executor) {
    }

    private final Queue<EnqueuedOperation> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean inProgress = new AtomicBoolean(false);
    private final Scheduler scheduler;
    private final TimeoutProvider timeoutProvider;
    private final AtomicReference<EnqueuedOperation> currentOperation = new AtomicReference<>();
    private OperationExecutor defaultExecutor;
    private Runnable timeoutTask;

    public OperationQueue(Scheduler scheduler, TimeoutProvider timeoutProvider) {
        this.scheduler = scheduler;
        this.timeoutProvider = timeoutProvider;
    }

    /**
     * Sets a default executor used when tryExecuteNext(...) is invoked without an explicit executor.
     * This is typically configured once by the GATT client.
     */
    public void setExecutor(OperationExecutor executor) {
        this.defaultExecutor = executor;
    }

    public <T> CompletableFuture<T> enqueue(BleOperation<T> op, BluetoothGatt gatt, OperationExecutor executor) {
        queue.add(new EnqueuedOperation(op, gatt, executor));
        tryExecuteNext();
        return op.getFuture();
    }

    public void tryExecuteNext() {
        tryExecuteNextInternal(null, null);
    }

    public void tryExecuteNext(BluetoothGatt gatt) {
        tryExecuteNextInternal(gatt, null);
    }

    public void onOperationFinished(@Nullable Object result) {
        EnqueuedOperation enqueued = currentOperation.getAndSet(null);
        if (enqueued != null) {
            enqueued.operation.complete(result);
        }
        inProgress.set(false);
        cancelTimeout();
        tryExecuteNext();
    }

    public void onOperationFailed(Throwable throwable) {
        EnqueuedOperation enqueued = currentOperation.getAndSet(null);
        if (enqueued != null) {
            enqueued.operation.completeExceptionally(throwable);
        }
        inProgress.set(false);
        cancelTimeout();
        tryExecuteNext();
    }

    public void clear(@NonNull BleDeviceAddress address) {
        queue.removeIf(enqueued -> {
            if (enqueued.operation.getAddress().equals(address)) {
                enqueued.operation.completeExceptionally(new RuntimeException("Queue cleared for " + address));
                return true;
            }
            return false;
        });

        EnqueuedOperation current = currentOperation.get();
        if (current != null && current.operation.getAddress().equals(address)) {
            if (currentOperation.compareAndSet(current, null)) {
                current.operation.completeExceptionally(new RuntimeException("Queue cleared for " + address));
                inProgress.set(false);
                cancelTimeout();
                tryExecuteNext();
            }
        }
    }

    public void clearAll() {
        queue.forEach(enqueued -> enqueued.operation.completeExceptionally(new RuntimeException("Queue cleared")));
        queue.clear();
        EnqueuedOperation enqueued = currentOperation.getAndSet(null);
        if (enqueued != null) {
            enqueued.operation.completeExceptionally(new RuntimeException("Queue cleared"));
        }
        inProgress.set(false);
        cancelTimeout();
    }

    private void tryExecuteNextInternal(BluetoothGatt explicitGatt, OperationExecutor explicitExecutor) {
        if (inProgress.get()) {
            return;
        }

        EnqueuedOperation next = queue.poll();
        if (next == null) {
            return;
        }

        BluetoothGatt gatt = explicitGatt != null ? explicitGatt : next.gatt;
        if (gatt == null) {
            // Should not happen if enqueued correctly, but let's be safe
            next.operation.completeExceptionally(new IllegalStateException("No GATT instance for operation"));
            tryExecuteNext();
            return;
        }

        OperationExecutor executor = explicitExecutor != null
                ? explicitExecutor
                : (next.executor != null ? next.executor : defaultExecutor);

        if (executor == null) {
            // Put it back or fail it? Let's fail it to avoid infinite loop if executor is missing
            next.operation.completeExceptionally(new IllegalStateException("No executor for operation"));
            tryExecuteNext();
            return;
        }

        currentOperation.set(next);
        inProgress.set(true);
        scheduleTimeout(gatt);
        Log.i(LOG_TAG, "Starting operation: " + next.operation.getType() + " for " + next.operation.getAddress());
        scheduler.post(() -> {
            try {
                executor.execute(gatt, next.operation);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Executor failed for " + next.operation.getType() + ": " + e.getMessage());
                onOperationFailed(e);
            }
        });
    }

    private void scheduleTimeout(BluetoothGatt gatt) {
        cancelTimeout();
        long timeoutMs = Math.max(1000, timeoutProvider.get());
        timeoutTask = () -> {
            try {
                Log.e(LOG_TAG, "GATT operation timed out after " + timeoutMs + " ms");
                EnqueuedOperation enqueued = currentOperation.getAndSet(null);
                if (enqueued != null) {
                    enqueued.operation.completeExceptionally(new RuntimeException("GATT operation timed out"));
                }
                try {
                    gatt.disconnect();
                } catch (SecurityException se) {
                    Log.w(LOG_TAG, "Missing BLUETOOTH_CONNECT permission while disconnecting on timeout: " + se);
                } catch (Exception ignore) {
                    // ignore other runtime issues while attempting to disconnect on timeout
                }
            } finally {
                inProgress.set(false);
                tryExecuteNext();
            }
        };
        scheduler.postDelayed(timeoutTask, timeoutMs);
    }

    private void cancelTimeout() {
        if (timeoutTask != null) {
            scheduler.removeCallbacks(timeoutTask);
            timeoutTask = null;
        }
    }

    public interface TimeoutProvider {
        long get();
    }
}
