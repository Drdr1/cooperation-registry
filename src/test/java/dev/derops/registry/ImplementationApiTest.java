package dev.derops.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.derops.registry.user.UserRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ImplementationApiTest extends IntegrationTest {

    @Autowired
    UserRepository users;

    private long agreementId(String number) throws Exception {
        return body(mvc.perform(get("/api/agreements?q=" + number).header(HttpHeaders.AUTHORIZATION, bearer("manager"))))
                .get("content").get(0).get("id").asLong();
    }

    private JsonNode itemOf(String username, String descriptionPrefix) throws Exception {
        for (JsonNode item : body(mvc.perform(get("/api/items/mine").header(HttpHeaders.AUTHORIZATION, bearer(username))))) {
            if (item.get("description").asText().startsWith(descriptionPrefix)) {
                return item;
            }
        }
        throw new AssertionError("No item '" + descriptionPrefix + "' for " + username);
    }

    private ResultActions progress(String username, JsonNode item, String status) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("status", status);
        req.put("progressNote", "Updated by " + username);
        req.put("version", item.get("version").asLong());
        return mvc.perform(post("/api/items/" + item.get("id").asLong() + "/progress")
                .header(HttpHeaders.AUTHORIZATION, bearer(username))
                .contentType(MediaType.APPLICATION_JSON).content(toJson(req)));
    }

    @Test
    void addingAnItemNotifiesTheResponsiblePersonInTheirLanguage() throws Exception {
        long executor2 = users.findWithRolesByUsername("executor2").orElseThrow().getId();
        Map<String, Object> req = new HashMap<>();
        req.put("description", "Draft the exchange regulations");
        req.put("responsibleId", executor2);
        req.put("dueDate", LocalDate.now().plusDays(20).toString());
        mvc.perform(post("/api/agreements/" + agreementId("INT-2026-003") + "/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer("manager"))
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.responsible.username").value("executor2"));

        JsonNode inbox = body(mvc.perform(get("/api/notifications")
                .header(HttpHeaders.AUTHORIZATION, bearer("executor2"))
                .header(HttpHeaders.ACCEPT_LANGUAGE, "uz")));
        JsonNode latest = inbox.get("content").get(0);
        assertThat(latest.get("type").asText()).isEqualTo("ITEM_ASSIGNED");
        assertThat(latest.get("message").asText()).startsWith("Sizga yangi band biriktirildi").contains("INT-2026-003");

        mvc.perform(get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, bearer("executor2")))
                .andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    void executorsUpdateOnlyTheirOwnItems() throws Exception {
        JsonNode own = itemOf("executor1", "Prepare a list");
        progress("executor1", own, "DONE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completedOn").value(LocalDate.now().toString()));

        JsonNode someoneElses = itemOf("executor2", "Launch the student");
        progress("executor1", someoneElses, "IN_PROGRESS")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("error.notResponsible"));

        // Managers (AGREEMENT_WRITE) may update any item.
        progress("manager", someoneElses, "IN_PROGRESS").andExpect(status().isOk());
    }

    @Test
    void viewersCannotReportProgress() throws Exception {
        progress("viewer", itemOf("executor1", "Prepare a list"), "DONE").andExpect(status().isForbidden());
    }

    @Test
    void overdueIsDerivedFromTheDueDate() throws Exception {
        JsonNode overdue = itemOf("executor1", "Organise a joint trade fair");
        assertThat(overdue.get("overdue").asBoolean()).isTrue();
        assertThat(itemOf("executor1", "Prepare a list").get("overdue").asBoolean()).isFalse();
    }

    @Test
    void staleProgressUpdateIsRejected() throws Exception {
        JsonNode item = itemOf("executor1", "Prepare a list");
        progress("executor1", item, "IN_PROGRESS").andExpect(status().isOk());
        progress("executor1", item, "DONE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.staleVersion"));
    }
}
