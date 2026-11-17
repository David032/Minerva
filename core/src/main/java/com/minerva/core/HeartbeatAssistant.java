package com.minerva.core;

/**
 * Executes the instructions in the configured heartbeat file using Minerva's assistant.
 */
@FunctionalInterface
public interface HeartbeatAssistant {
    /**
     * Sends heartbeat instructions to the assistant.
     *
     * @param instructions contents of {@code heartbeat.md}
     * @return assistant response
     */
    String run(String instructions);
}
