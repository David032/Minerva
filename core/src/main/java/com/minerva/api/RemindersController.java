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

@RestController
@RequestMapping("/api/reminders")
public class RemindersController {
    private final ReminderService reminderService;

    public RemindersController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping
    public List<ReminderResponse> list() {
        return reminderService.listActive();
    }

    @PostMapping
    public ResponseEntity<ReminderResponse> create(@RequestBody ReminderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                reminderService.create(request.time(), request.message(), request.timeZone())
        );
    }

    @DeleteMapping("/{id}")
    public ReminderResponse cancel(@PathVariable UUID id) {
        return reminderService.cancel(id);
    }

    @PostMapping("/{id}/acknowledge")
    public ReminderResponse acknowledge(@PathVariable UUID id) {
        return reminderService.acknowledge(id);
    }

    @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class})
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.getMessage()));
    }

    public record ReminderRequest(String time, String message, String timeZone) {
    }
}
