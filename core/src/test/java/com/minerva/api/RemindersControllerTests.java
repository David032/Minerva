package com.minerva.api;

import com.minerva.core.reminders.ReminderResponse;
import com.minerva.core.reminders.ReminderService;
import com.minerva.core.reminders.ReminderStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("Unit")
class RemindersControllerTests {
    private final ReminderService service = mock(ReminderService.class);
    private final RemindersController controller = new RemindersController(service);

    @Test
    void createsReminderUsingRequestTimeZone() {
        var id = UUID.randomUUID();
        var response = new ReminderResponse(
                id,
                "Take a break",
                Instant.parse("2026-11-17T11:00:00Z"),
                "Europe/London",
                ReminderStatus.SCHEDULED,
                Instant.parse("2026-11-17T10:00:00Z"),
                Instant.parse("2026-11-17T10:00:00Z")
        );
        when(service.create("2026-11-17T11:00", "Take a break", "Europe/London")).thenReturn(response);

        var result = controller.create(new RemindersController.ReminderRequest(
                "2026-11-17T11:00",
                "Take a break",
                "Europe/London"
        ));

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals(response, result.getBody());
        verify(service).create("2026-11-17T11:00", "Take a break", "Europe/London");
    }

    @Test
    void cancelsAndAcknowledgesReminderById() {
        var id = UUID.randomUUID();

        controller.cancel(id);
        controller.acknowledge(id);

        verify(service).cancel(id);
        verify(service).acknowledge(id);
    }
}
