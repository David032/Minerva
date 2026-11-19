package com.minerva.core.journiv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persistent mapping from a Journiv account to Minerva's dedicated journal. */
@Entity
@Table(name = "journiv_identity")
public class JournivIdentity {
    @Id
    @Column(length = 1000)
    private String account;

    @Column(name = "journal_id", nullable = false, length = 64)
    private String journalId;

    protected JournivIdentity() {
    }

    /**
     * Creates a mapping for an account.
     *
     * @param account server and account identity
     * @param journalId Journiv journal identifier
     */
    public JournivIdentity(String account, String journalId) {
        this.account = account;
        this.journalId = journalId;
    }

    /** @return Journiv journal identifier */
    public String getJournalId() { return journalId; }
}
