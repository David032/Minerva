package com.minerva.core.journiv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An activity waiting for summarisation and delivery to Journiv.
 */
@Entity
@Table(name = "journiv_outbox")
public class JournivEvent {
    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "journal_date", nullable = false)
    private LocalDate journalDate;

    @Column(length = 4000)
    private String summary;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false, length = 1000)
    private String account;

    @Column(name = "request_text", columnDefinition = "text")
    private String requestText;

    @Column(name = "response_text", columnDefinition = "text")
    private String responseText;

    protected JournivEvent() {
    }

    /**
     * Creates an outbox event with a stable identifier for retry reconciliation.
     *
     * @param occurredAt time of the activity
     * @param journalDate local journal date
     * @param kind activity type
     * @param account destination server and account
     * @param requestText user input, or blank for a heartbeat
     * @param responseText assistant response
     */
    public JournivEvent(Instant occurredAt, LocalDate journalDate, String kind, String account,
                        String requestText, String responseText) {
        this.id = UUID.randomUUID();
        this.occurredAt = occurredAt;
        this.journalDate = journalDate;
        this.kind = kind;
        this.account = account;
        this.requestText = requestText;
        this.responseText = responseText;
    }

    /** @return stable event identifier */
    public UUID getId() { return id; }

    /** @return activity time */
    public Instant getOccurredAt() { return occurredAt; }

    /** @return local date assigned to the activity */
    public LocalDate getJournalDate() { return journalDate; }

    /** @return concise activity summary */
    public String getSummary() { return summary; }

    /** @return activity type */
    public String getKind() { return kind; }

    /** @return original destination server and account */
    public String getAccount() { return account; }

    /** @return original request awaiting summarisation */
    public String getRequestText() { return requestText; }

    /** @return original response awaiting summarisation */
    public String getResponseText() { return responseText; }

    /**
     * Stores a summary and removes the original text from the pending event.
     *
     * @param summary generated concise summary
     */
    public void summarise(String summary) {
        if (summary == null || summary.isBlank() || summary.length() > 4000) {
            throw new IllegalArgumentException("Journiv summary must contain between 1 and 4000 characters");
        }
        this.summary = summary.strip();
        this.requestText = null;
        this.responseText = null;
    }
}
