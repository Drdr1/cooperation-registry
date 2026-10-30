package dev.derops.registry.audit;

public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    STATUS_CHANGE,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    EXPORT,
    EXTERNAL_CHECK
}
