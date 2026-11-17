package com.minerva.core;

import com.minerva.utils.FileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;

/**
 * Loads and executes the user-configured heartbeat instructions when Minerva is idle.
 */
@Service
public class HeartbeatService {
    private static final Logger logger = LoggerFactory.getLogger(HeartbeatService.class);

    private final AiActivityGate activityGate;
    private final FileManager fileManager;
    private final HeartbeatAssistant assistant;

    /**
     * Creates a heartbeat service.
     *
     * @param activityGate gate shared with foreground AI requests
     * @param fileManager Minerva local data paths
     * @param assistant heartbeat-specific assistant operation
     */
    public HeartbeatService(
            AiActivityGate activityGate,
            FileManager fileManager,
            HeartbeatAssistant assistant
    ) {
        this.activityGate = activityGate;
        this.fileManager = fileManager;
        this.assistant = assistant;
    }

    /**
     * Attempts one hourly heartbeat run. Busy ticks are skipped and are not queued.
     *
     * @return {@code true} if the idle slot was acquired, otherwise {@code false}
     */
    public boolean runIfIdle() {
        boolean ran = activityGate.tryRunHeartbeat(this::runHeartbeat);
        if (!ran) {
            logger.info("Skipping heartbeat because an AI conversation is active");
        }
        return ran;
    }

    private void runHeartbeat() {
        var heartbeatPath = fileManager.getHeartbeatFilePath();
        String instructions;
        try {
            instructions = Files.readString(heartbeatPath, StandardCharsets.UTF_8);
        } catch (NoSuchFileException exception) {
            logger.info("Skipping heartbeat because {} does not exist", heartbeatPath);
            return;
        } catch (IOException exception) {
            logger.error("Could not read heartbeat instructions from {}", heartbeatPath, exception);
            return;
        }

        if (instructions.isBlank()) {
            logger.info("Skipping heartbeat because {} is empty", heartbeatPath);
            return;
        }

        assistant.run(instructions);
        logger.info("Heartbeat completed");
    }
}
