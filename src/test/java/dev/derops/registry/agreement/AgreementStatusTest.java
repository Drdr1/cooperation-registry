package dev.derops.registry.agreement;

import static dev.derops.registry.agreement.AgreementStatus.DRAFT;
import static dev.derops.registry.agreement.AgreementStatus.EXPIRED;
import static dev.derops.registry.agreement.AgreementStatus.IN_FORCE;
import static dev.derops.registry.agreement.AgreementStatus.SIGNED;
import static dev.derops.registry.agreement.AgreementStatus.SUSPENDED;
import static dev.derops.registry.agreement.AgreementStatus.TERMINATED;
import static dev.derops.registry.agreement.AgreementStatus.UNDER_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AgreementStatusTest {

    @Test
    void happyPath() {
        assertThat(DRAFT.canMoveTo(UNDER_REVIEW)).isTrue();
        assertThat(UNDER_REVIEW.canMoveTo(SIGNED)).isTrue();
        assertThat(SIGNED.canMoveTo(IN_FORCE)).isTrue();
        assertThat(IN_FORCE.canMoveTo(SUSPENDED)).isTrue();
        assertThat(SUSPENDED.canMoveTo(IN_FORCE)).isTrue();
        assertThat(IN_FORCE.canMoveTo(EXPIRED)).isTrue();
    }

    @Test
    void noShortcuts() {
        assertThat(DRAFT.canMoveTo(SIGNED)).isFalse();
        assertThat(DRAFT.canMoveTo(IN_FORCE)).isFalse();
        assertThat(UNDER_REVIEW.canMoveTo(IN_FORCE)).isFalse();
        assertThat(SIGNED.canMoveTo(DRAFT)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = AgreementStatus.class, names = {"EXPIRED", "TERMINATED"})
    void finalStatusesHaveNoExit(AgreementStatus status) {
        assertThat(status.isFinal()).isTrue();
        assertThat(status.nextStatuses()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = AgreementStatus.class, names = {"EXPIRED", "TERMINATED"}, mode = EnumSource.Mode.EXCLUDE)
    void everyOpenStatusCanBeTerminated(AgreementStatus status) {
        assertThat(status.canMoveTo(TERMINATED)).isTrue();
    }

    @Test
    void onlySigningNeedsApproval() {
        for (AgreementStatus s : AgreementStatus.values()) {
            assertThat(s.requiresApproval()).isEqualTo(s == SIGNED);
        }
    }
}
