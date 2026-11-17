package com.minerva.core.reminders;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("Unit")
class ReminderTimeParserTests {
    private final ReminderTimeParser parser = new ReminderTimeParser("UTC");

    @Test
    void interpretsLocalDateTimeInRequestedZone() {
        var resolved = parser.parse("2026-11-06T12:30", "America/New_York");

        assertEquals("2026-11-06T17:30:00Z", resolved.instant().toString());
        assertEquals("America/New_York", resolved.timeZone());
    }

    @Test
    void preservesExplicitOffsetInsteadOfUsingRequestedZone() {
        var resolved = parser.parse("2026-11-06T12:30:00-05:00", "Europe/London");

        assertEquals("2026-11-06T17:30:00Z", resolved.instant().toString());
        assertEquals("-05:00", resolved.timeZone());
    }

    @Test
    void normalizesUtcOffsetToBrowserCompatibleZoneId() {
        var resolved = parser.parse("2026-11-06T12:30:00Z", null);

        assertEquals("UTC", resolved.timeZone());
    }

    @Test
    void rejectsLocalTimeInDaylightSavingGap() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("2026-03-29T02:30", "Europe/Berlin"));
    }

    @Test
    void choosesEarlierOffsetForDaylightSavingOverlap() {
        var resolved = parser.parse("2026-10-25T02:30", "Europe/Berlin");

        assertEquals("2026-10-25T00:30:00Z", resolved.instant().toString());
    }

    @Test
    void rejectsUnknownRequestedTimeZone() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("2026-11-06T12:30", "Not/AZone"));
    }
}
