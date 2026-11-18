package com.minerva.core.journiv;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Tag("Unit")
class JournivClientTests {
    private static final String BASE = "http://journiv.test";
    private static final String ENTRY_URL = BASE
            + "/api/v1/entries/journal/journal-1?limit=100&offset=0&include_drafts=true";
    private final JournivSettings settings = new JournivSettings(BASE, "minerva@example.org", "secret", "UTC");
    private final JournivIdentityRepository identities = mock(JournivIdentityRepository.class);
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final JournivClient client = new JournivClient(settings, identities, builder);

    @Test
    void registersAndCreatesJournalAndDailyMoment() {
        expectRegistrationAndLogin();
        server.expect(requestTo(BASE + "/api/v1/journals/?include_archived=true"))
                .andRespond(json("[]"));
        server.expect(requestTo(BASE + "/api/v1/journals/"))
                .andExpect(method(POST))
                .andExpect(content().json("{\"title\":\"Minerva\"}"))
                .andRespond(json("{\"id\":\"journal-1\",\"title\":\"Minerva\",\"entry_count\":0}"));
        server.expect(requestTo(ENTRY_URL)).andRespond(json("[]"));
        JournivEvent event = event();
        server.expect(requestTo(BASE + "/api/v1/moments"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {"logged_date_tz":"2026-03-29","logged_timezone":"UTC",
                         "entry":{"journal_id":"journal-1","title":"Minerva - 2026-03-29",
                         "content_delta":{"ops":[{"insert":"[minerva-event:%s] Discussed a book.\\n"}]}}}
                        """.formatted(event.getId())))
                .andRespond(withSuccess());

        client.append(event);

        verify(identities).save(any(JournivIdentity.class));
        server.verify();
    }

    @Test
    void appendsWithConflictProtectionAndRecognisesSuccessfulRetry() {
        expectRegistrationAndLogin();
        when(identities.findById(settings.account()))
                .thenReturn(Optional.of(new JournivIdentity(settings.account(), "journal-1")));
        server.expect(requestTo(BASE + "/api/v1/journals/journal-1"))
                .andRespond(json("{\"id\":\"journal-1\",\"title\":\"Minerva\"}"));
        JournivEvent event = event();
        server.expect(requestTo(ENTRY_URL)).andRespond(json(entry("Earlier notes\\n")));
        server.expect(requestTo(BASE + "/api/v1/entries/entry-1"))
                .andExpect(method(PUT))
                .andExpect(content().json("""
                        {"expected_updated_at":"2026-03-29T12:00:00Z",
                         "content_delta":{"ops":[{"insert":"Earlier notes\\n"},
                         {"insert":"[minerva-event:%s] Discussed a book.\\n"}]}}
                        """.formatted(event.getId())))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        server.expect(requestTo(ENTRY_URL))
                .andRespond(json(entry("Earlier notes\\n[minerva-event:" + event.getId() + "] Discussed a book.\\n")));

        assertThrows(RestClientResponseException.class, () -> client.append(event));
        client.append(event);
        server.verify();
    }

    @Test
    void rejectsDuplicateDailyEntriesRatherThanOverwritingEither() {
        expectRegistrationAndLogin();
        when(identities.findById(settings.account()))
                .thenReturn(Optional.of(new JournivIdentity(settings.account(), "journal-1")));
        server.expect(requestTo(BASE + "/api/v1/journals/journal-1"))
                .andRespond(json("{\"id\":\"journal-1\",\"title\":\"Minerva\"}"));
        String one = entry("first").replaceFirst("^\\[", "").replaceFirst("\\]$", "");
        server.expect(requestTo(ENTRY_URL)).andRespond(json("[" + one + "," + one + "]"));

        assertThrows(IllegalStateException.class, () -> client.append(event()));
        server.verify();
    }

    @Test
    void reusesRegisteredAccountAndReadsPaginatedEntriesWithoutMarkers() {
        server.expect(requestTo(BASE + "/api/v1/auth/register"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"Email already registered\"}"));
        server.expect(requestTo(BASE + "/api/v1/auth/login"))
                .andRespond(json("{\"access_token\":\"access\",\"user\":{}}"));
        when(identities.findById(settings.account()))
                .thenReturn(Optional.of(new JournivIdentity(settings.account(), "journal-1")));
        server.expect(requestTo(BASE + "/api/v1/journals/journal-1"))
                .andRespond(json("{\"id\":\"journal-1\",\"title\":\"Minerva\"}"));
        server.expect(requestTo(BASE + "/api/v1/entries/journal/journal-1?limit=1&offset=2&include_drafts=true"))
                .andRespond(json(entry("[minerva-event:11111111-1111-1111-1111-111111111111] Old note")));

        assertEquals("Minerva - 2026-03-29\nOld note", client.readEntries(2, 1).getFirst());
        server.verify();
    }

    @Test
    void doesNotRegisterAgainAfterLoginFailure() {
        server.expect(requestTo(BASE + "/api/v1/auth/register"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"Email already registered\"}"));
        server.expect(requestTo(BASE + "/api/v1/auth/login"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(RestClientResponseException.class, () -> client.readEntries(0, 1));
        server.verify();
    }

    private void expectRegistrationAndLogin() {
        server.expect(requestTo(BASE + "/api/v1/auth/register")).andRespond(withStatus(HttpStatus.CREATED));
        server.expect(requestTo(BASE + "/api/v1/auth/login"))
                .andRespond(json("{\"access_token\":\"access\",\"token_type\":\"bearer\",\"user\":{\"name\":\"Minerva\"}}"));
    }

    private JournivEvent event() {
        var event = new JournivEvent(Instant.parse("2026-03-29T12:00:00Z"),
                LocalDate.parse("2026-03-29"), "text", settings.account(), "request", "response");
        event.summarise("Discussed a book.");
        return event;
    }

    private static String entry(String text) {
        return """
                [{"id":"entry-1","title":"Minerva - 2026-03-29","updated_at":"2026-03-29T12:00:00Z",
                "content_delta":{"ops":[{"insert":"%s"}]}}]
                """.formatted(text);
    }

    private static org.springframework.test.web.client.ResponseCreator json(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }
}
