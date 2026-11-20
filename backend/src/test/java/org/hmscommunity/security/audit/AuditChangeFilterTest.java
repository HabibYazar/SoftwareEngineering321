package org.hmscommunity.security.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuditChangeFilterTest {

    private final AuditChangeFilter filter = new AuditChangeFilter(Set.of("phone", "address"));

    @Test
    void recordsBeforeAndAfterForAllowListedChangedFields() {
        assertThat(filter.changes(
                Map.of("phone", "05550000001", "address", "Synthetic St. 1"),
                Map.of("phone", "05550000002", "address", "Synthetic St. 1")))
            .containsExactly(new FieldChange("phone", "05550000001", "05550000002"));
    }

    @Test
    void unchangedFieldsProduceNoChanges() {
        assertThat(filter.changes(Map.of("phone", "1"), Map.of("phone", "1"))).isEmpty();
    }

    @Test
    void nonAllowListedFieldsAreTrackedButTheirValuesRedacted() {
        assertThat(filter.changes(Map.of("diagnosis", "secret-medical-text"), Map.of("diagnosis", "other")))
            .containsExactly(new FieldChange("diagnosis", AuditChangeFilter.REDACTED, AuditChangeFilter.REDACTED));
    }

    @Test
    void addedAndRemovedValuesKeepNullSide() {
        Map<String, String> before = new HashMap<>();
        before.put("phone", null);

        assertThat(filter.changes(before, Map.of("phone", "1")))
            .containsExactly(new FieldChange("phone", null, "1"));
        assertThat(filter.changes(Map.of("diagnosis", "x"), Map.of()))
            .containsExactly(new FieldChange("diagnosis", AuditChangeFilter.REDACTED, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "passwordHash", "accessToken", "clientSecret", "Authorization", "credentials"})
    void secretFieldsCanNeverBeAllowListed(String field) {
        assertThatThrownBy(() -> new AuditChangeFilter(Set.of(field)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void secretFieldValuesAreAlwaysRedacted() {
        assertThat(filter.changes(Map.of("password", "old-pass"), Map.of("password", "new-pass")))
            .containsExactly(new FieldChange("password", AuditChangeFilter.REDACTED, AuditChangeFilter.REDACTED));
    }
}
