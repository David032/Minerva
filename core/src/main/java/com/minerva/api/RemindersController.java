package com.minerva.api;

import com.minerva.core.reminders.ReminderResponse;
import com.minerva.core.reminders.ReminderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * HTTP API for listing, creating, cancelling, and acknowledging reminders.
 */
@RestController
@RequestMapping("/api/reminders")
public class RemindersController {
    private final ReminderService reminderService;

    /**
     * Creates the HTTP controller backed by the reminder service.
     *
     * @param reminderService reminder lifecycle service
     */
    public RemindersController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    /**
     * Lists scheduled and due reminders for the chat page.
     *
     * @return active reminders ordered by scheduled time
     */
    @GetMapping
    public List<ReminderResponse> list() {
        return reminderService.listActive();
    }

    /**
     * Creates a reminder using the requested time zone or Minerva's configured default.
     *
     * @param request reminder time, message, and optional IANA time zone
     * @return created reminder with HTTP 201
     */
    @PostMapping
    public ResponseEntity<ReminderResponse> create(@RequestBody ReminderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                reminderService.create(request.time(), request.message(), request.timeZone())
        );
    }

    /**
     * Cancels a scheduled or due reminder.
     *
     * @param id reminder identifier
     * @return updated reminder
     */
    @DeleteMapping("/{id}")
    public ReminderResponse cancel(@PathVariable UUID id) {
        return reminderService.cancel(id);
    }

    /**
     * Acknowledges a due reminder.
     *
     * @param id reminder identifier
     * @return updated reminder
     */
    @PostMapping("/{id}/acknowledge")
    public ReminderResponse acknowledge(@PathVariable UUID id) {
        return reminderService.acknowledge(id);
    }

    /**
     * Maps invalid reminder input to an HTTP 400 response.
     *
     * @param exception validation failure
     * @return error response
     */
    @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class})
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    /**
     * Maps missing reminder identifiers to an HTTP 404 response.
     *
     * @param exception missing reminder
     * @return error response
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", exception.getMessage()));
    }

    /**
     * Maps invalid lifecycle transitions to an HTTP 409 response.
     *
     * @param exception rejected transition
     * @return error response
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.getMessage()));
    }

    /**
     * JSON input for creating a reminder.
     *
     * @param time ISO-8601 date/time
     * @param message reminder text
     * @param timeZone optional IANA zone for time input without an explicit offset or zone
     */
    public record ReminderRequest(String time, String message, String timeZone) {
    }
}
