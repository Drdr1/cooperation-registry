package dev.derops.registry.agreement;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Life cycle of an agreement. The transition table is the single source of
 * truth: the API rejects anything not listed here with 409.
 */
public enum AgreementStatus {
    DRAFT,
    UNDER_REVIEW,
    SIGNED,
    IN_FORCE,
    SUSPENDED,
    EXPIRED,
    TERMINATED;

    private static final Map<AgreementStatus, Set<AgreementStatus>> TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(UNDER_REVIEW, TERMINATED),
            UNDER_REVIEW, EnumSet.of(DRAFT, SIGNED, TERMINATED),
            SIGNED, EnumSet.of(IN_FORCE, TERMINATED),
            IN_FORCE, EnumSet.of(SUSPENDED, EXPIRED, TERMINATED),
            SUSPENDED, EnumSet.of(IN_FORCE, TERMINATED),
            EXPIRED, EnumSet.noneOf(AgreementStatus.class),
            TERMINATED, EnumSet.noneOf(AgreementStatus.class));

    public boolean canMoveTo(AgreementStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public Set<AgreementStatus> nextStatuses() {
        Set<AgreementStatus> next = EnumSet.noneOf(AgreementStatus.class);
        next.addAll(TRANSITIONS.get(this));
        return next;
    }

    /** Moving to SIGNED is the approval step and needs AGREEMENT_APPROVE on top of AGREEMENT_WRITE. */
    public boolean requiresApproval() {
        return this == SIGNED;
    }

    public boolean isFinal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
