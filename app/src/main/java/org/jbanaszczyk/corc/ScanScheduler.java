package org.jbanaszczyk.corc;

import android.os.Handler;
import android.os.Looper;

public final class ScanScheduler {

    private static final int MSG_START_SCAN = 1;

    private final Handler handler;

    public ScanScheduler(Runnable scanTickCallback) {
        this.handler = new Handler(Looper.getMainLooper(), msg -> {
            if (msg.what == MSG_START_SCAN) {
                scanTickCallback.run();
                return true;
            }
            return false;
        });
    }

    public void schedule(long delayMs) {
        handler.removeMessages(MSG_START_SCAN);
        handler.sendEmptyMessageDelayed(MSG_START_SCAN, delayMs);
    }

    public void cancelAll() {
        handler.removeCallbacksAndMessages(null);
    }
}
