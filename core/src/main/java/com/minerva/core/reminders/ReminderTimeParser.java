package com.minerva.core.reminders;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalQueries;
import java.util.List;

@Component
public class ReminderTimeParser {
    private final ZoneId defaultTimeZone;

    public ReminderTimeParser(@Value("${minerva.reminders.time-zone:UTC}") String defaultTimeZone) {
        this.defaultTimeZone = ZoneId.of(defaultTimeZone);
    }

    public ResolvedTime parse(String value, String requestedTimeZone) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A reminder date and time is required");
        }

        try {
            var parsed = DateTimeFormatter.ISO_DATE_TIME.parse(value);
            var localDateTime = LocalDateTime.from(parsed);
            ZoneId explicitZone = parsed.query(TemporalQueries.zone());
            ZoneOffset explicitOffset = parsed.query(TemporalQueries.offset());

            if (explicitOffset != null) {
                if (explicitZone != null && !(explicitZone instanceof ZoneOffset)) {
                    var zonedDateTime = java.time.ZonedDateTime.from(parsed);
                    return new ResolvedTime(zonedDateTime.toInstant(), zonedDateTime.getZone().getId());
                }
                return new ResolvedTime(localDateTime.toInstant(explicitOffset), zoneId(explicitOffset));
            }

            ZoneId zone = explicitZone != null
                    ? explicitZone
                    : requestedTimeZone == null || requestedTimeZone.isBlank()
                            ? defaultTimeZone
                            : ZoneId.of(requestedTimeZone);
            List<ZoneOffset> validOffsets = zone.getRules().getValidOffsets(localDateTime);
            if (validOffsets.isEmpty()) {
                throw new IllegalArgumentException("The selected local time does not exist because of a daylight-saving transition");
            }
            // ZoneRules lists the earlier offset first for an ambiguous local time.
            return new ResolvedTime(localDateTime.toInstant(validOffsets.getFirst()), zoneId(zone));
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid reminder date/time or time zone", exception);
        }
    }

    private String zoneId(ZoneId zone) {
        return zone instanceof ZoneOffset offset && offset.equals(ZoneOffset.UTC) ? "UTC" : zone.getId();
    }

    public record ResolvedTime(Instant instant, String timeZone) {
    }
}
