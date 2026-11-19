package com.minerva.core.journiv;

import com.minerva.core.AiActivityGate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("Unit")
class JournivServiceTests {
    private final JournivSettings settings = new JournivSettings(
            "https://journiv.test", "minerva@example.org", "secret", "Europe/London");
    private final JournivEventRepository events = mock(JournivEventRepository.class);
    private final JournivClient client = mock(JournivClient.class);
    private final ChatClient.Builder builder = mock(ChatClient.Builder.class);
    private final ChatClient summariser = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final AiActivityGate gate = new AiActivityGate();
    private final Clock clock = Clock.fixed(Instant.parse("2026-03-29T23:30:00Z"), ZoneOffset.UTC);

    private JournivService service() {
        when(builder.clone()).thenReturn(builder);
        when(builder.build()).thenReturn(summariser);
        return new JournivService(settings, events, client, builder, gate, clock);
    }

    @Test
    void recordsActivityOnTheConfiguredLocalDate() {
        service().record("text", "Hello", "Good morning");

        var event = ArgumentCaptor.forClass(JournivEvent.class);
        verify(events).save(event.capture());
        assertEquals(LocalDate.parse("2026-03-30"), event.getValue().getJournalDate());
        assertEquals("Hello", event.getValue().getRequestText());
        assertEquals(settings.account(), event.getValue().getAccount());
    }

    @Test
    void summarisesBeforeDeliveryAndRemovesOriginalText() {
        var event = new JournivEvent(clock.instant(), LocalDate.parse("2026-03-30"),
                "text", settings.account(), "private request", "private response");
        when(events.findTop50ByOrderByOccurredAtAsc()).thenReturn(List.of(event));
        when(summariser.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("Discussed a private matter.");

        service().deliverPending();

        assertEquals("Discussed a private matter.", event.getSummary());
        assertNull(event.getRequestText());
        assertNull(event.getResponseText());
        verify(events).save(event);
        verify(client).append(event);
        verify(events).delete(event);
    }

    @Test
    void keepsSummarisedEventForRetryAfterDeliveryFailure() {
        var event = new JournivEvent(clock.instant(), LocalDate.parse("2026-03-30"),
                "heartbeat", settings.account(), "", "Checked the news");
        event.summarise("Reviewed the news.");
        when(events.findTop50ByOrderByOccurredAtAsc()).thenReturn(List.of(event));
        doThrow(new IllegalStateException("Unavailable")).when(client).append(event);

        service().deliverPending();

        verify(events, never()).delete(event);
    }

    @Test
    void refusesToDeliverActivityToAnotherAccount() {
        var event = new JournivEvent(clock.instant(), LocalDate.parse("2026-03-30"),
                "text", "another account", "request", "response");
        when(events.findTop50ByOrderByOccurredAtAsc()).thenReturn(List.of(event));

        service().deliverPending();

        verify(client, never()).append(any());
        verify(events, never()).delete(any());
    }

    @Test
    void rejectsIncompleteOrUnsafeConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new JournivSettings("https://user:pass@example.org", "a", "b", "UTC"));
        assertThrows(IllegalArgumentException.class,
                () -> new JournivSettings("https://example.org", "", "b", "UTC"));
    }

    @Test
    void doesNotRecordWhenIntegrationIsDisabled() {
        var disabled = new JournivSettings("", "", "", "UTC");
        when(builder.clone()).thenReturn(builder);
        when(builder.build()).thenReturn(summariser);
        var service = new JournivService(disabled, events, client, builder, gate, clock);

        service.record("text", "Hello", "Good morning");
        service.deliverPending();

        verify(events, never()).save(any());
        verify(events, never()).findTop50ByOrderByOccurredAtAsc();
    }
}
