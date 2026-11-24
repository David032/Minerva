package com.minerva.core;

import com.minerva.api.personality.MediaPersonalityService;
import com.minerva.api.personality.PersonalityImportJobService;
import com.minerva.core.personality.PersonalityImportJobRepository;
import com.minerva.core.personality.PersonalityImportJobStatus;
import com.minerva.core.reminders.ReminderRepository;
import com.minerva.core.reminders.ReminderScheduler;
import com.minerva.core.reminders.ReminderService;
import com.minerva.core.reminders.ReminderTimeParser;
import com.minerva.utils.FileManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.FixedDelayTask;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("Unit")
class PollingScheduleTests {
    @Test
    void registersDefaultsAndRunsExpectedIdleChecksInSimulatedMinute() {
        var reminders = mock(ReminderRepository.class);
        var imports = mock(PersonalityImportJobRepository.class);
        try (var context = context(reminders, imports, Map.of())) {
            var reminderTask = task(context, "markDueReminders");
            var importTask = task(context, "dispatchQueuedJobs");
            assertEquals(Duration.ofSeconds(5), reminderTask.getIntervalDuration());
            assertEquals(Duration.ofSeconds(10), importTask.getIntervalDuration());

            runSimulatedMinute(reminderTask);
            runSimulatedMinute(importTask);

            verify(reminders, times(12)).markDueBefore(any(), any(), any());
            verify(imports, times(6)).findAllByStatusInOrderByCreatedAtAsc(
                    List.of(PersonalityImportJobStatus.QUEUED));
        }
    }

    @Test
    void appliesConfiguredPollingOverrides() {
        try (var context = context(mock(ReminderRepository.class), mock(PersonalityImportJobRepository.class),
                Map.of("minerva.reminders.poll-interval", "2500",
                        "minerva.personality.import-poll-interval", "7000"))) {
            assertEquals(Duration.ofMillis(2500), task(context, "markDueReminders").getIntervalDuration());
            assertEquals(Duration.ofMillis(7000), task(context, "dispatchQueuedJobs").getIntervalDuration());
        }
    }

    @Test
    void appliesEnvironmentStylePollingOverrides() {
        try (var context = context(mock(ReminderRepository.class), mock(PersonalityImportJobRepository.class),
                Map.of("MINERVA_REMINDERS_POLL_INTERVAL", "3200",
                        "MINERVA_PERSONALITY_IMPORT_POLL_INTERVAL", "8400"))) {
            assertEquals(Duration.ofMillis(3200), task(context, "markDueReminders").getIntervalDuration());
            assertEquals(Duration.ofMillis(8400), task(context, "dispatchQueuedJobs").getIntervalDuration());
        }
    }

    @Test
    void previousOneSecondIntervalsPerform120IdleChecksInSimulatedMinute() {
        var reminders = mock(ReminderRepository.class);
        var imports = mock(PersonalityImportJobRepository.class);
        try (var context = context(reminders, imports,
                Map.of("minerva.reminders.poll-interval", "1000",
                        "minerva.personality.import-poll-interval", "1000"))) {
            runSimulatedMinute(task(context, "markDueReminders"));
            runSimulatedMinute(task(context, "dispatchQueuedJobs"));

            verify(reminders, times(60)).markDueBefore(any(), any(), any());
            verify(imports, times(60)).findAllByStatusInOrderByCreatedAtAsc(
                    List.of(PersonalityImportJobStatus.QUEUED));
        }
    }

    private static AnnotationConfigApplicationContext context(
            ReminderRepository reminders, PersonalityImportJobRepository imports, Map<String, Object> properties) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-overrides", properties));
        var scheduler = mock(TaskScheduler.class);
        when(scheduler.getClock()).thenReturn(Clock.systemUTC());
        context.registerBean("taskScheduler", TaskScheduler.class, () -> scheduler);
        context.registerBean(ReminderScheduler.class, () -> new ReminderScheduler(
                new ReminderService(reminders, new ReminderTimeParser("UTC"), Clock.systemUTC(), 1000)));
        context.registerBean(PersonalityImportJobService.class, () -> new PersonalityImportJobService(
                imports, mock(MediaPersonalityService.class), mock(FileManager.class), mock(TaskExecutor.class)));
        context.register(SchedulingConfiguration.class);
        context.refresh();
        return context;
    }

    private static FixedDelayTask task(AnnotationConfigApplicationContext context, String method) {
        return context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks().stream()
                .map(scheduled -> (FixedDelayTask) scheduled.getTask())
                .filter(scheduled -> scheduled.getRunnable().toString().endsWith("." + method))
                .findFirst().orElseThrow();
    }

    private static void runSimulatedMinute(FixedDelayTask task) {
        for (long elapsed = 0; elapsed < 60_000; elapsed += task.getIntervalDuration().toMillis()) {
            task.getRunnable().run();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @PropertySource("classpath:application.properties")
    static class SchedulingConfiguration {
    }
}
