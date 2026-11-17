package com.minerva.api;

import com.minerva.core.AiBusyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Returns a clear HTTP response when a background heartbeat temporarily owns the AI slot.
 */
@RestControllerAdvice
public class AiBusyExceptionHandler {
    /**
     * Maps a rejected AI request to HTTP 503 with a user-facing retry message.
     *
     * @param exception temporary AI busy condition
     * @return plain-text retry message
     */
    @ExceptionHandler(AiBusyException.class)
    public ResponseEntity<String> handleAiBusy(AiBusyException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.TEXT_PLAIN)
                .body(exception.getMessage());
    }
}
