package com.minerva.core;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers the optional heartbeat once per configured hourly schedule.
 */
@Component
@ConditionalOnProperty(name = "minerva.heartbeat.enabled", havingValue = "true", matchIfMissing = true)
public class HeartbeatScheduler {
    private final HeartbeatService heartbeatService;

    /**
     * Creates the scheduled heartbeat trigger.
     *
     * @param heartbeatService heartbeat execution service
     */
    public HeartbeatScheduler(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    /**
     * Runs on the top of each hour by default; a busy tick is skipped.
     */
    @Scheduled(
            cron = "${minerva.heartbeat.cron:0 0 * * * *}",
            zone = "${minerva.heartbeat.zone:UTC}"
    )
    public void runHourlyHeartbeat() {
        heartbeatService.runIfIdle();
    }
}
