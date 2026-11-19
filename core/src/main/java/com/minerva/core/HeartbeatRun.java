package com.minerva.core;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Correlates tool invocations with one heartbeat, even if they run on another thread.
 */
public final class HeartbeatRun {
    static final String CONTEXT_KEY = HeartbeatRun.class.getName();

    private final String id = UUID.randomUUID().toString();
    private final AtomicInteger actions = new AtomicInteger();

    /** @return the identifier of this heartbeat run */
    public String id() {
        return id;
    }

    void actionStarted() {
        actions.incrementAndGet();
    }

    /** @return the number of tool invocations started during this run */
    public int actionCount() {
        return actions.get();
    }
}
