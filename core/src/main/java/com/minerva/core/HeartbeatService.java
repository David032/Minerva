package com.minerva.core;

import com.minerva.utils.FileManager;
import com.minerva.core.journiv.JournivService;
import com.minerva.core.routines.RoutineRunner;
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
    private final RoutineRunner routineRunner;

    /**
     * Creates a heartbeat service.
     *
     * @param activityGate gate shared with foreground AI requests
     * @param fileManager Minerva local data paths
     * @param assistant heartbeat-specific assistant operation
     * @param journivService optional Journiv activity recorder
     * @param routineRunner executes one eligible managed routine
     */
    public HeartbeatService(
            AiActivityGate activityGate,
            FileManager fileManager,
            HeartbeatAssistant assistant,
            JournivService journivService,
            RoutineRunner routineRunner
    ) {
        this.activityGate = activityGate;
        this.fileManager = fileManager;
        this.assistant = assistant;
        this.journivService = journivService;
        this.routineRunner = routineRunner;
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
        if (!routineRunner.runOne()) {
            return;
        }
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

        HeartbeatRun run = new HeartbeatRun();
        logger.info("Heartbeat run={} started", run.id());
        try {
            String result = assistant.run(instructions, run);
            journivService.record("heartbeat", "", result);
            logger.info("Heartbeat run={} completed actions={} responseCharacters={} response={}", run.id(),
                    run.actionCount(), result == null ? 0 : result.length(), ActivityLogText.bounded(result));
        } catch (RuntimeException exception) {
            logger.error("Heartbeat run={} failed actions={} type={}", run.id(), run.actionCount(),
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }
}
