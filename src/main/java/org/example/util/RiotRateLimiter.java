package org.example.util;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Simple sliding window rate limiter tuned for Riot API constraints.
 * Uses two independent windows (e.g., 20 req / 1s and 100 req / 2min) and
 * blocks until sending another request stays within both windows.
 */
public class RiotRateLimiter {
    private final int shortLimit;
    private final long shortWindowNanos;
    private final int longLimit;
    private final long longWindowNanos;
    private final Deque<Long> shortWindow = new ArrayDeque<>();
    private final Deque<Long> longWindow = new ArrayDeque<>();

    public RiotRateLimiter(int shortLimit, Duration shortWindow, int longLimit, Duration longWindow) {
        this.shortLimit = shortLimit;
        this.shortWindowNanos = shortWindow == null ? 0 : shortWindow.toNanos();
        this.longLimit = longLimit;
        this.longWindowNanos = longWindow == null ? 0 : longWindow.toNanos();
    }

    /**
     * Blocks until another request fits inside both configured windows.
     */
    public void acquire() throws InterruptedException {
        while (true) {
            long sleepMillis;
            synchronized (this) {
                long now = System.nanoTime();
                prune(shortWindow, now, shortWindowNanos);
                prune(longWindow, now, longWindowNanos);

                boolean shortOk = shortLimit <= 0 || shortWindow.size() < shortLimit;
                boolean longOk = longLimit <= 0 || longWindow.size() < longLimit;
                if (shortOk && longOk) {
                    shortWindow.addLast(now);
                    longWindow.addLast(now);
                    return;
                }

                long waitNanos = 0;
                if (!shortOk && !shortWindow.isEmpty()) {
                    waitNanos = Math.max(waitNanos, shortWindowNanos - (now - shortWindow.peekFirst()));
                }
                if (!longOk && !longWindow.isEmpty()) {
                    waitNanos = Math.max(waitNanos, longWindowNanos - (now - longWindow.peekFirst()));
                }
                // Convert to millis with ceiling (never sleep 0)
                sleepMillis = Math.max(1L, (waitNanos + 999_999L) / 1_000_000L);
            }
            Thread.sleep(sleepMillis);
        }
    }

    private void prune(Deque<Long> queue, long now, long windowNanos) {
        if (windowNanos <= 0) {
            queue.clear();
            return;
        }
        long threshold = now - windowNanos;
        while (!queue.isEmpty() && queue.peekFirst() <= threshold) {
            queue.removeFirst();
        }
    }
}
