package com.minerva.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("Unit")
class AiActivityGateTests {
    private final AiActivityGate gate = new AiActivityGate();

    @Test
    void skipsHeartbeatWhileForegroundConversationIsActive() {
        var heartbeatRan = new AtomicBoolean();

        try (var ignored = gate.beginConversation()) {
            assertFalse(gate.tryRunHeartbeat(() -> heartbeatRan.set(true)));
        }

        assertFalse(heartbeatRan.get());
        assertTrue(gate.tryRunHeartbeat(() -> heartbeatRan.set(true)));
        assertTrue(heartbeatRan.get());
    }

    @Test
    void releasesIdleSlotWhenHeartbeatFails() {
        assertThrows(IllegalStateException.class, () -> gate.tryRunHeartbeat(() -> {
            throw new IllegalStateException("heartbeat failed");
        }));

        var foregroundStarted = new AtomicBoolean();
        try (var ignored = gate.beginConversation()) {
            foregroundStarted.set(true);
        }
        assertTrue(foregroundStarted.get());
    }
}
