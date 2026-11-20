package com.minerva.core.personality;

/**
 * Lifecycle states for a persisted personality import.
 */
public enum PersonalityImportJobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED
}
