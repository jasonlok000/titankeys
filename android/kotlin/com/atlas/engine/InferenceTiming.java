package com.atlas.engine;

import android.os.SystemClock;
import android.util.Log;

/** Fixed-size aggregate windows. No text, candidates or gesture samples are retained. */
final class InferenceTiming {
    private final String operation;
    private int count, failures, cancellations;
    private long queueTotal, runTotal, callbackTotal, queueMax, runMax, callbackMax;

    InferenceTiming(String operation) { this.operation = operation; }

    static final class Request {
        final long enqueued = SystemClock.uptimeMillis();
        long started, finished;
        void start() { started = SystemClock.uptimeMillis(); }
        void finish() { finished = SystemClock.uptimeMillis(); }
    }

    synchronized void record(Request request, boolean failed, boolean cancelled, boolean delivered) {
        long queue = request.started - request.enqueued;
        long run = request.finished - request.started;
        long callback = delivered ? SystemClock.uptimeMillis() - request.finished : 0;
        queueTotal += queue; runTotal += run; callbackTotal += callback;
        queueMax = Math.max(queueMax, queue); runMax = Math.max(runMax, run); callbackMax = Math.max(callbackMax, callback);
        if (failed) failures++;
        if (cancelled) cancellations++;
        if (++count < 64) return;
        Log.d("TouchpadSwipe", operation + " latency ms mean/max: queue " + queueTotal / count + "/" + queueMax
                + ", run " + runTotal / count + "/" + runMax + ", callback " + callbackTotal / count + "/" + callbackMax
                + "; failures " + failures + ", cancelled " + cancellations + "/" + count);
        count = failures = cancellations = 0;
        queueTotal = runTotal = callbackTotal = queueMax = runMax = callbackMax = 0;
    }
}
