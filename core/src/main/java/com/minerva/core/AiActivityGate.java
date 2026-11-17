package com.minerva.core;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Optional;

/**
 * Coordinates foreground AI requests with exclusive background heartbeat runs.
 *
 * <p>Foreground requests may run concurrently with one another. A heartbeat can start only
 * when no foreground request is active, and prevents new requests from overlapping it.</p>
 */
@Component
public class AiActivityGate {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    /**
     * Tries to acquire a foreground activity slot without waiting.
     *
     * @return a lease that releases the slot when closed, or empty while a heartbeat is running
     */
    public Optional<ConversationLease> tryBeginConversation() {
        Lock conversationLock = lock.readLock();
        if (!conversationLock.tryLock()) {
            return Optional.empty();
        }
        return Optional.of(new ConversationLease(conversationLock));
    }

    /**
     * Runs a heartbeat only if no foreground AI request is currently active.
     *
     * @param heartbeat heartbeat operation
     * @return {@code true} if the operation acquired the idle slot and ran, otherwise {@code false}
     */
    public boolean tryRunHeartbeat(Runnable heartbeat) {
        Lock heartbeatLock = lock.writeLock();
        if (!heartbeatLock.tryLock()) {
            return false;
        }
        try {
            heartbeat.run();
            return true;
        } finally {
            heartbeatLock.unlock();
        }
    }

    /**
     * A foreground activity slot released with try-with-resources.
     */
    public static final class ConversationLease implements AutoCloseable {
        private final Lock lock;

        private ConversationLease(Lock lock) {
            this.lock = lock;
        }

        @Override
        public void close() {
            lock.unlock();
        }
    }
}
