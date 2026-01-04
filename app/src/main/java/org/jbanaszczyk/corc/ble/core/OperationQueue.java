package org.jbanaszczyk.corc.ble.core;

import android.bluetooth.BluetoothGatt;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Single-threaded BLE operation queue with per-operation timeout.
 */
public final class OperationQueue {
    private static final Logger LOGGER = Logger.getLogger("CORC:OpQueue");
    public static final int QUEUE_CAPACITY = 64;

    public interface TimeoutProvider { long get(); }

    private final Queue<BleOperation<?>> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean inProgress = new AtomicBoolean(false);
    private final Scheduler scheduler;
    private final TimeoutProvider timeoutProvider;
    private OperationExecutor defaultExecutor;
    private Runnable timeoutTask;
    private BleOperation<?> currentOperation;

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
        queue.add(op);
        tryExecuteNextInternal(gatt, executor);
        return op.getFuture();
    }

    public void tryExecuteNext(BluetoothGatt gatt) {
        tryExecuteNextInternal(gatt, null);
    }

    public void onOperationFinished(Object result) {
        if (currentOperation != null) {
            currentOperation.complete(result);
            currentOperation = null;
        }
        inProgress.set(false);
        cancelTimeout();
    }

    public void onOperationFailed(Throwable throwable) {
        if (currentOperation != null) {
            currentOperation.completeExceptionally(throwable);
            currentOperation = null;
        }
        inProgress.set(false);
        cancelTimeout();
    }

    public void clear() {
        queue.forEach(op -> op.completeExceptionally(new RuntimeException("Queue cleared")));
        queue.clear();
        if (currentOperation != null) {
            currentOperation.completeExceptionally(new RuntimeException("Queue cleared"));
            currentOperation = null;
        }
        inProgress.set(false);
        cancelTimeout();
    }

    private void tryExecuteNextInternal(BluetoothGatt gatt, OperationExecutor explicitExecutor) {
        if (gatt == null) return;
        if (inProgress.get()) return;
        // Determine executor first; if none, do not disturb queue/inProgress
        OperationExecutor executor = explicitExecutor != null ? explicitExecutor : defaultExecutor;
        if (executor == null) return;
        BleOperation<?> next = queue.poll();
        if (next == null) return;
        currentOperation = next;
        inProgress.set(true);
        scheduleTimeout(gatt);
        scheduler.post(() -> executor.execute(gatt, next));
    }

    private void scheduleTimeout(BluetoothGatt gatt) {
        cancelTimeout();
        long timeoutMs = Math.max(1000, timeoutProvider.get());
        timeoutTask = () -> {
            try {
                LOGGER.severe("GATT operation timed out after " + timeoutMs + " ms");
                if (currentOperation != null) {
                    currentOperation.completeExceptionally(new RuntimeException("GATT operation timed out"));
                    currentOperation = null;
                }
                try {
                    gatt.disconnect();
                } catch (SecurityException se) {
                    LOGGER.warning("Missing BLUETOOTH_CONNECT permission while disconnecting on timeout: " + se);
                } catch (Exception ignore) {
                    // ignore other runtime issues while attempting to disconnect on timeout
                }
            } finally {
                inProgress.set(false);
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
}
