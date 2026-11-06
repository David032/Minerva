package com.minerva.tools;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("Unit")
class DateTimeToolsTests {
    private final DateTimeTools tools = new DateTimeTools();

    @Test
    void setsAlarmFromIsoDateTime() {
        assertEquals("Alarm set for 2026-11-06T12:30", tools.setAlarm("2026-11-06T12:30"));
    }

    @Test
    void rejectsMalformedAlarmDateTime() {
        assertThrows(DateTimeException.class, () -> tools.setAlarm("tomorrow at noon"));
    }

    @Test
    void returnsCurrentTimeAsZonedDateTime() {
        assertDoesNotThrow(() -> ZonedDateTime.parse(tools.getCurrentDateTime()));
    }
}
