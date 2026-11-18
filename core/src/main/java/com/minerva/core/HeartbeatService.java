package com.minerva.core;

import com.minerva.utils.FileManager;
import com.minerva.core.journiv.JournivService;
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
    private final JournivService journivService;

    /**
     * Creates a heartbeat service.
     *
     * @param activityGate gate shared with foreground AI requests
     * @param fileManager Minerva local data paths
     * @param assistant heartbeat-specific assistant operation
     * @param journivService optional Journiv activity recorder
     */
    public HeartbeatService(
            AiActivityGate activityGate,
            FileManager fileManager,
            HeartbeatAssistant assistant,
            JournivService journivService
    ) {
        this.activityGate = activityGate;
        this.fileManager = fileManager;
        this.assistant = assistant;
        this.journivService = journivService;
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
            fileManager.ensureDefaultHeartbeatFile();
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

        String result = assistant.run(instructions);
        journivService.record("heartbeat", "", result);
        logger.info("Heartbeat completed");
    }
}
