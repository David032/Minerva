package com.minerva.tools;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Tag("Unit")
class DateTimeToolsTests {
    private final DateTimeTools tools = new DateTimeTools("UTC");

    @Test
    void returnsCurrentTimeAsZonedDateTime() {
        assertDoesNotThrow(() -> ZonedDateTime.parse(tools.getCurrentDateTime()));
    }
}
