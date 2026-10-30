package dev.derops.registry.user;

/**
 * Fine-grained permissions. Roles are bundles of these and are editable by an
 * administrator; code only ever checks permissions, never role names.
 */
public enum Permission {
    MEETING_READ,
    MEETING_WRITE,
    AGREEMENT_READ,
    AGREEMENT_WRITE,
    AGREEMENT_APPROVE,
    ITEM_UPDATE,
    REPORT_EXPORT,
    USER_ADMIN,
    REFERENCE_ADMIN,
    AUDIT_READ
}
