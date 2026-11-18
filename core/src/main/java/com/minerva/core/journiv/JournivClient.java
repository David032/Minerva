package com.minerva.core.journiv;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Journiv API client for a dedicated account and journal.
 *
 * <p>Writes are serialised within this process. A stable event marker allows a retry to
 * recognise a write whose response was lost.</p>
 */
@Component
public class JournivClient {
    private static final String JOURNAL_TITLE = "Minerva";
    private static final int PAGE_SIZE = 100;
    private static final ParameterizedTypeReference<List<Journal>> JOURNALS = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Entry>> ENTRIES = new ParameterizedTypeReference<>() {};
    private final JournivSettings settings;
    private final JournivIdentityRepository identities;
    private final RestClient client;
    private String token;
    private boolean registrationAttempted;
    private String journalId;

    /**
     * Creates a client with bounded connection and response timeouts.
     *
     * @param settings Journiv configuration
     * @param identities persisted account-to-journal mappings
     */
    @Autowired
    public JournivClient(JournivSettings settings, JournivIdentityRepository identities) {
        this(settings, identities, createBuilder());
    }

    JournivClient(JournivSettings settings, JournivIdentityRepository identities, RestClient.Builder builder) {
        this.settings = settings;
        this.identities = identities;
        this.client = builder.baseUrl(settings.url()).build();
    }

    private static RestClient.Builder createBuilder() {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().requestFactory(factory);
    }

    /**
     * Applies an event to its daily entry, or recognises an earlier successful attempt.
     *
     * @param event persistent activity summary
     */
    public synchronized void append(JournivEvent event) {
        if (event.getSummary() == null) {
            throw new IllegalArgumentException("Journiv event has not been summarised");
        }
        String id = journalId();
        String title = "Minerva - " + event.getJournalDate();
        String marker = "[minerva-event:" + event.getId() + "]";
        List<Entry> matches = allEntries(id).stream().filter(entry -> title.equals(entry.title())).toList();
        if (matches.size() > 1) {
            throw new JournivException("Multiple Journiv entries exist for " + event.getJournalDate());
        }
        String text = marker + " " + event.getSummary() + "\n";
        if (matches.isEmpty()) {
            var delta = new Delta(List.of(new Op(text, null)));
            var body = Map.of(
                    "logged_at_utc", event.getOccurredAt().toString(),
                    "logged_date_tz", event.getJournalDate().toString(),
                    "logged_timezone", settings.zone().getId(),
                    "entry", Map.of("journal_id", id, "title", title, "content_delta", delta));
            authorised(access -> client.post().uri("/api/v1/moments")
                    .headers(headers -> headers.setBearerAuth(access)).body(body)
                    .retrieve().toBodilessEntity());
            return;
        }
        Entry entry = matches.getFirst();
        if (entry.content_delta() == null || entry.content_delta().ops() == null || entry.updated_at() == null) {
            throw new JournivException("Journiv entry is missing its content or update timestamp");
        }
        String currentText = entry.content_delta().ops().stream()
                .map(op -> op.insert() instanceof String value ? value : "")
                .collect(java.util.stream.Collectors.joining());
        if (currentText.contains(marker)) {
            return;
        }
        var operations = new ArrayList<>(entry.content_delta().ops());
        operations.add(new Op(text, null));
        var body = Map.of("content_delta", new Delta(operations), "expected_updated_at", entry.updated_at());
        authorised(access -> client.put().uri("/api/v1/entries/{id}", entry.id())
                .headers(headers -> headers.setBearerAuth(access)).body(body)
                .retrieve().toBodilessEntity());
    }

    /**
     * Reads one bounded page of entries belonging to Minerva's journal.
     *
     * @param offset zero-based offset into the journal
     * @param limit number of entries, from 1 to 100
     * @return entry titles and text
     */
    public synchronized List<String> readEntries(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > PAGE_SIZE) {
            throw new IllegalArgumentException("Offset must be non-negative and limit must be between 1 and 100");
        }
        return entries(journalId(), offset, limit).stream().map(entry -> {
            String text = entry.content_plain_text();
            if (text == null && entry.content_delta() != null && entry.content_delta().ops() != null) {
                text = entry.content_delta().ops().stream()
                        .map(op -> op.insert() instanceof String value ? value : "")
                        .collect(java.util.stream.Collectors.joining());
            }
            return entry.title() + "\n" + (text == null ? "" : text)
                    .replaceAll("\\[minerva-event:[0-9a-fA-F-]+\\]\\s*", "");
        }).toList();
    }

    private List<Entry> allEntries(String id) {
        var result = new ArrayList<Entry>();
        for (int offset = 0; ; offset += PAGE_SIZE) {
            List<Entry> page = entries(id, offset, PAGE_SIZE);
            result.addAll(page);
            if (page.size() < PAGE_SIZE) {
                return result;
            }
        }
    }

    private List<Entry> entries(String id, int offset, int limit) {
        return requireBody(authorised(access -> client.get()
                .uri(builder -> builder.path("/api/v1/entries/journal/{id}")
                        .queryParam("limit", limit).queryParam("offset", offset)
                        .queryParam("include_drafts", true).build(id))
                .headers(headers -> headers.setBearerAuth(access)).retrieve().body(ENTRIES)));
    }

    private String journalId() {
        if (journalId != null) {
            return journalId;
        }
        String account = settings.account();
        var identity = identities.findById(account);
        if (identity.isPresent()) {
            String id = identity.get().getJournalId();
            Journal existing = requireBody(authorised(access -> client.get().uri("/api/v1/journals/{id}", id)
                    .headers(headers -> headers.setBearerAuth(access)).retrieve().body(Journal.class)));
            if (!id.equals(existing.id())) {
                throw new JournivException("Journiv returned an unexpected journal identity");
            }
            journalId = id;
            return id;
        }
        List<Journal> journals = requireBody(authorised(access -> client.get()
                .uri("/api/v1/journals/?include_archived=true")
                .headers(headers -> headers.setBearerAuth(access)).retrieve().body(JOURNALS)));
        List<Journal> matches = journals.stream().filter(journal -> JOURNAL_TITLE.equals(journal.title())).toList();
        if (matches.size() > 1) {
            throw new JournivException("Multiple Journiv journals named Minerva exist");
        }
        Journal journal = matches.isEmpty()
                ? requireBody(authorised(access -> client.post().uri("/api/v1/journals/")
                    .headers(headers -> headers.setBearerAuth(access)).body(Map.of("title", JOURNAL_TITLE))
                    .retrieve().body(Journal.class)))
                : matches.getFirst();
        if (journal.id() == null || journal.id().isBlank()) {
            throw new JournivException("Journiv returned a journal without an ID");
        }
        identities.save(new JournivIdentity(account, journal.id()));
        journalId = journal.id();
        return journalId;
    }

    private <T> T authorised(Function<String, T> operation) {
        ensureAuthenticated();
        try {
            return operation.apply(token);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 401) {
                throw exception;
            }
            token = null;
            login();
            return operation.apply(token);
        }
    }

    private void ensureAuthenticated() {
        if (!settings.enabled()) {
            throw new JournivException("Journiv integration is not configured");
        }
        if (token != null) {
            return;
        }
        if (!registrationAttempted) {
            try {
                client.post().uri("/api/v1/auth/register")
                        .body(Map.of("email", settings.email(), "password", settings.password(), "name", "Minerva"))
                        .retrieve().toBodilessEntity();
            } catch (RestClientResponseException exception) {
                boolean alreadyRegistered = exception.getStatusCode().value() == 400
                        && exception.getResponseBodyAsString().contains("Email already registered");
                if (!alreadyRegistered && exception.getStatusCode().value() != 403) {
                    throw exception;
                }
            }
            registrationAttempted = true;
        }
        login();
    }

    private void login() {
        Login response = requireBody(client.post().uri("/api/v1/auth/login")
                .body(Map.of("email", settings.email(), "password", settings.password()))
                .retrieve().body(Login.class));
        if (response.access_token() == null || response.access_token().isBlank()) {
            throw new JournivException("Journiv login returned no access token");
        }
        token = response.access_token();
    }

    private static <T> T requireBody(T body) {
        if (body == null) {
            throw new JournivException("Journiv returned an empty response");
        }
        return body;
    }

    record Login(String access_token) {}
    record Journal(String id, String title) {}
    record Entry(String id, String title, String updated_at, Delta content_delta, String content_plain_text) {}
    record Delta(List<Op> ops) {}
    record Op(Object insert, Map<String, Object> attributes) {}
}
