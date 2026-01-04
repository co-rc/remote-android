package org.jbanaszczyk.corc.ble.core;

/**
 * Small abstraction over Android's Handler to allow using the queue in unit tests
 * without android.os.* dependencies.
 */
public interface Scheduler {
    void post(Runnable task);

    void postDelayed(Runnable task, long delayMillis);

    void removeCallbacks(Runnable task);
}
