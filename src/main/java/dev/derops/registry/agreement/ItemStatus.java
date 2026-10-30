package dev.derops.registry.agreement;

/** "Overdue" is not stored: it is derived from the due date so it can never go stale. */
public enum ItemStatus {
    NOT_STARTED,
    IN_PROGRESS,
    DONE
}
