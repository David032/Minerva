package com.minerva.core.journiv;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises the client against a disposable, externally supplied Journiv server.
 */
@Tag("ContinuousIntegration")
@EnabledIfEnvironmentVariable(named = "JOURNIV_TEST_URL", matches = ".+")
class JournivIntegrationTests {
    @Test
    void registersAndReusesAccountAndDailyEntry() {
        String url = System.getenv("JOURNIV_TEST_URL");
        String email = "minerva-test-" + UUID.randomUUID() + "@example.com";
        String password = "Minerva-test-" + UUID.randomUUID();
        var settings = new JournivSettings(url, email, password, "UTC");
        var identities = mock(JournivIdentityRepository.class);
        var storedIdentity = new AtomicReference<JournivIdentity>();
        when(identities.findById(any())).thenAnswer(invocation -> Optional.ofNullable(storedIdentity.get()));
        when(identities.save(any(JournivIdentity.class))).thenAnswer(invocation -> {
            JournivIdentity identity = invocation.getArgument(0);
            storedIdentity.set(identity);
            return identity;
        });
        var first = event(settings, "Discussed a book.");
        var second = event(settings, "Reviewed the weather.");
        try {
            var client = new JournivClient(settings, identities);
            client.append(first);
            client.append(first);
            client.append(second);

            var restarted = new JournivClient(settings, identities);
            var entries = restarted.readEntries(0, 10);
            assertEquals(1, entries.size());
            assertTrue(entries.getFirst().contains("Discussed a book."));
            assertTrue(entries.getFirst().contains("Reviewed the weather."));
        } finally {
            var http = RestClient.builder().baseUrl(url).build();
            var login = http.post().uri("/api/v1/auth/login")
                    .body(Map.of("email", email, "password", password))
                    .retrieve().body(JournivClient.Login.class);
            if (login != null && login.access_token() != null) {
                http.delete().uri("/api/v1/users/me")
                        .headers(headers -> headers.setBearerAuth(login.access_token()))
                        .retrieve().toBodilessEntity();
            }
        }
    }

    private static JournivEvent event(JournivSettings settings, String summary) {
        var event = new JournivEvent(Instant.parse("2026-03-29T12:00:00Z"),
                LocalDate.parse("2026-03-29"), "text", settings.account(), "request", "response");
        event.summarise(summary);
        return event;
    }
}
