package com.minerva.api.personality;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configures the single-worker executor used for durable personality imports.
 */
@Configuration
public class PersonalityImportJobConfiguration {
    /**
     * Creates a serial executor so personality imports do not concurrently contend for the local model.
     *
     * @return background executor for personality import jobs
     */
    @Bean("personalityImportExecutor")
    public ThreadPoolTaskExecutor personalityImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("personality-import-");
        return executor;
    }
}
