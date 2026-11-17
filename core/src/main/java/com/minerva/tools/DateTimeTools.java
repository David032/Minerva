package com.minerva.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.ZoneId;

@Component
public class DateTimeTools {
    private final ZoneId timeZone;

    public DateTimeTools(@Value("${minerva.reminders.time-zone:UTC}") String timeZone) {
        this.timeZone = ZoneId.of(timeZone);
    }

    @Tool(description = "Get the current date and time in Minerva's configured timezone")
    String getCurrentDateTime() {
        return ZonedDateTime.now(timeZone).toString();
    }
}
