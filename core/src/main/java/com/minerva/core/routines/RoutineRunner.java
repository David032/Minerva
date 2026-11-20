package com.minerva.core.routines;

import com.minerva.core.HeartbeatAssistant;
import com.minerva.core.HeartbeatRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Executes at most one routine per idle tick, with a bounded wait for the assistant. */
@Component
public class RoutineRunner {
    private static final Logger logger = LoggerFactory.getLogger(RoutineRunner.class);
    private final RoutineService service;
    private final HeartbeatAssistant assistant;
    private final long timeoutSeconds;
    private final AtomicBoolean active = new AtomicBoolean();

    /** Creates a runner with a timeout between one second and ten minutes. */
    public RoutineRunner(RoutineService service, HeartbeatAssistant assistant,
                         @Value("${minerva.routines.timeout-seconds:120}") long timeoutSeconds) {
        if (timeoutSeconds < 1 || timeoutSeconds > 600) throw new IllegalArgumentException("Routine timeout must be between 1 and 600 seconds");
        this.service = service;
        this.assistant = assistant;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Attempts one routine inside the caller's heartbeat gate.
     * @return false if an assistant is still running and the normal heartbeat must be skipped
     */
    public boolean runOne() {
        if (!active.compareAndSet(false, true)) {
            logger.warn("Skipping routine tick because an earlier timed-out attempt is still running");
            return false;
        }
        RoutineService.Attempt attempt;
        try {
            attempt = service.begin();
        } catch (RuntimeException exception) {
            active.set(false);
            throw exception;
        }
        if (attempt == null) {
            active.set(false);
            return true;
        }
        var done = new CountDownLatch(1);
        var response = new AtomicReference<String>();
        var failure = new AtomicReference<Throwable>();
        var worker = new Thread(() -> {
            try {
                response.set(assistant.run("Routine: " + attempt.name() + "\n\n" + attempt.instructions(), new HeartbeatRun()));
            } catch (Throwable exception) {
                failure.set(exception);
            } finally {
                active.set(false);
                done.countDown();
            }
        }, "minerva-routine");
        worker.setDaemon(true);
        worker.start();
        try {
            if (!done.await(timeoutSeconds, TimeUnit.SECONDS)) {
                worker.interrupt();
                service.finish(attempt.runId(), "TIMED_OUT", "The attempt exceeded its time limit; cancellation was requested.");
                logger.warn("Routine {} timed out; cancellation requested", attempt.routineId());
                return false;
            }
            if (failure.get() != null) {
                String detail = failure.get().getClass().getSimpleName() + ": " + String.valueOf(failure.get().getMessage());
                service.finish(attempt.runId(), "FAILED", detail);
                logger.warn("Routine {} failed with {}", attempt.routineId(), failure.get().getClass().getSimpleName());
            } else if (response.get() == null) {
                service.finish(attempt.runId(), "FAILED", "Assistant returned no response.");
                logger.warn("Routine {} returned no response", attempt.routineId());
            } else {
                service.finish(attempt.runId(), "SUCCEEDED", response.get());
            }
            return true;
        } catch (InterruptedException exception) {
            worker.interrupt();
            service.finish(attempt.runId(), "INTERRUPTED", "The heartbeat was interrupted; cancellation was requested.");
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
