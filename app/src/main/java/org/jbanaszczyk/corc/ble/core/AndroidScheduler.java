package org.jbanaszczyk.corc.ble.core;

import android.os.Handler;
import androidx.annotation.NonNull;

/**
 * Android-backed Scheduler based on Handler. Keeps OperationQueue free from direct
 * android.os.* dependencies to allow usage in unit tests.
 */
public final class AndroidScheduler implements Scheduler {
    private final Handler handler;

    public AndroidScheduler(@NonNull Handler handler) {
        this.handler = handler;
    }

    @Override
    public void post(Runnable task) {
        handler.post(task);
    }

    @Override
    public void postDelayed(Runnable task, long delayMillis) {
        handler.postDelayed(task, delayMillis);
    }

    @Override
    public void removeCallbacks(Runnable task) {
        handler.removeCallbacks(task);
    }
}
