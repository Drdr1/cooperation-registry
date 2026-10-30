package dev.derops.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.derops.registry.audit.AuditAction;
import dev.derops.registry.audit.AuditEntry;
import dev.derops.registry.audit.AuditRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AgreementApiTest extends IntegrationTest {

    @Autowired
    AuditRepository audit;

    private Map<String, Object> newAgreement(String number) {
        Map<String, Object> req = new HashMap<>();
        req.put("registrationNumber", number);
        req.put("title", "Test agreement " + number);
        return req;
    }

    private JsonNode create(String token, String number) throws Exception {
        return body(mvc.perform(post("/api/agreements").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(newAgreement(number))))
                .andExpect(status().isCreated()));
    }

    private ResultActions move(String token, long id, String status, String comment) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("status", status);
        req.put("comment", comment);
        return mvc.perform(post("/api/agreements/" + id + "/status").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(req)));
    }

    @Test
    void newAgreementStartsAsDraftWithAllowedNextSteps() throws Exception {
        JsonNode a = create(bearer("manager"), "T-100");
        assertThat(a.get("status").asText()).isEqualTo("DRAFT");
        assertThat(a.get("nextStatuses").toString()).contains("UNDER_REVIEW").contains("TERMINATED").doesNotContain("IN_FORCE");
        assertThat(a.get("createdBy").asText()).isEqualTo("manager");
    }

    @Test
    void transitionsOutsideTheLifeCycleAreRejected() throws Exception {
        String manager = bearer("manager");
        long id = create(manager, "T-101").get("id").asLong();
        move(manager, id, "IN_FORCE", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.invalidTransition"));
    }

    @Test
    void signingRequiresApprovalPermission() throws Exception {
        String manager = bearer("manager");
        long id = create(manager, "T-102").get("id").asLong();
        move(manager, id, "UNDER_REVIEW", "Ready").andExpect(status().isOk());

        move(manager, id, "SIGNED", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("error.approvalRequired"));

        move(bearer("admin"), id, "SIGNED", "Approved")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"))
                .andExpect(jsonPath("$.signedOn").isNotEmpty());

        JsonNode detail = body(mvc.perform(get("/api/agreements/" + id).header(HttpHeaders.AUTHORIZATION, manager)));
        JsonNode history = detail.get("history");
        assertThat(history).hasSize(3);
        assertThat(history.get(2).get("toStatus").asText()).isEqualTo("SIGNED");
        assertThat(history.get(2).get("changedBy").asText()).isEqualTo("admin");

        List<AuditEntry> trail = audit.findByEntityTypeAndEntityIdOrderByIdAsc("AGREEMENT", id);
        assertThat(trail).extracting(AuditEntry::getAction)
                .containsExactly(AuditAction.CREATE, AuditAction.STATUS_CHANGE, AuditAction.STATUS_CHANGE);
    }

    @Test
    void terminationNeedsAReason() throws Exception {
        String manager = bearer("manager");
        long id = create(manager, "T-103").get("id").asLong();
        move(manager, id, "TERMINATED", "  ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("error.reasonRequired"));
        move(manager, id, "TERMINATED", "Partner withdrew").andExpect(status().isOk());

        Map<String, Object> edit = newAgreement("T-103");
        edit.put("version", 1);
        mvc.perform(put("/api/agreements/" + id).header(HttpHeaders.AUTHORIZATION, manager)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(edit)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.agreementClosed"));
    }

    @Test
    void updatesUseOptimisticLockingAndAreAuditedFieldByField() throws Exception {
        String manager = bearer("manager");
        JsonNode created = create(manager, "T-104");
        long id = created.get("id").asLong();
        long version = created.get("version").asLong();

        Map<String, Object> edit = newAgreement("T-104");
        edit.put("title", "Renamed agreement");
        edit.put("version", version);
        mvc.perform(put("/api/agreements/" + id).header(HttpHeaders.AUTHORIZATION, manager)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(edit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed agreement"));

        // Same (now stale) version again: someone else changed the record meanwhile.
        mvc.perform(put("/api/agreements/" + id).header(HttpHeaders.AUTHORIZATION, manager)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(edit)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.staleVersion"));

        AuditEntry update = audit.findByEntityTypeAndEntityIdOrderByIdAsc("AGREEMENT", id).stream()
                .filter(e -> e.getAction() == AuditAction.UPDATE).findFirst().orElseThrow();
        assertThat(update.getDetails()).containsOnlyKeys("title");
        assertThat(update.getDetails().get("title").toString()).contains("Renamed agreement");
    }

    @Test
    void validationAndDuplicateErrors() throws Exception {
        String manager = bearer("manager");
        Map<String, Object> invalid = new HashMap<>();
        invalid.put("registrationNumber", "bad number with spaces");
        mvc.perform(post("/api/agreements").header(HttpHeaders.AUTHORIZATION, manager)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.registrationNumber").exists());

        mvc.perform(post("/api/agreements").header(HttpHeaders.AUTHORIZATION, manager)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(newAgreement("INT-2026-001"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.duplicateRegistrationNumber"));
    }

    @Test
    void readOnlyRolesCannotWrite() throws Exception {
        mvc.perform(post("/api/agreements").header(HttpHeaders.AUTHORIZATION, bearer("viewer"))
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(newAgreement("T-105"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchFiltersAndLocalizesLabels() throws Exception {
        JsonNode page = body(mvc.perform(get("/api/agreements?status=IN_FORCE&sort=registrationNumber,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer("viewer"))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ru"))
                .andExpect(status().isOk()));
        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        assertThat(page.get("content").get(0).get("registrationNumber").asText()).isEqualTo("INT-2026-001");
        assertThat(page.get("content").get(0).get("statusLabel").asText()).isEqualTo("Действует");
        assertThat(page.get("content").get(0).get("country").get("name").asText()).isEqualTo("Республика Корея");

        mvc.perform(get("/api/agreements?q=digital").header(HttpHeaders.AUTHORIZATION, bearer("viewer")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].registrationNumber").value("INT-2026-002"));

        mvc.perform(get("/api/agreements?sort=nonexistent").header(HttpHeaders.AUTHORIZATION, bearer("viewer")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void externalRegistryCheckStoresTheReference() throws Exception {
        JsonNode list = body(mvc.perform(get("/api/agreements?q=INT-2026-001").header(HttpHeaders.AUTHORIZATION, bearer("manager"))));
        long id = list.get("content").get(0).get("id").asLong();

        mvc.perform(post("/api/agreements/" + id + "/external-check").header(HttpHeaders.AUTHORIZATION, bearer("manager")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.externalRef").value("EXT-INT-2026-001"));

        mvc.perform(get("/api/agreements/" + id).header(HttpHeaders.AUTHORIZATION, bearer("viewer")))
                .andExpect(jsonPath("$.agreement.externalRef").value("EXT-INT-2026-001"))
                .andExpect(jsonPath("$.items.length()").value(3));
    }
}
