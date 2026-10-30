package dev.derops.registry;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.derops.registry.reference.ReferenceKind;
import dev.derops.registry.reference.ReferenceRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class MeetingApiTest extends IntegrationTest {

    @Autowired
    ReferenceRepository references;

    private Map<String, Object> meeting(String title) {
        Map<String, Object> req = new HashMap<>();
        req.put("title", title);
        req.put("heldOn", LocalDate.now().plusDays(10).toString());
        req.put("countryId", references.findByKindAndCode(ReferenceKind.COUNTRY, "FR").orElseThrow().getId());
        req.put("formatId", references.findByKindAndCode(ReferenceKind.MEETING_FORMAT, "BILATERAL").orElseThrow().getId());
        return req;
    }

    @Test
    void createUpdateAndFilter() throws Exception {
        String manager = bearer("manager");
        JsonNode created = body(mvc.perform(post("/api/meetings").header(HttpHeaders.AUTHORIZATION, manager)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(meeting("Consultations on culture"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.statusLabel").value("Planned"))
                .andExpect(jsonPath("$.country.name").value("France")));

        Map<String, Object> edit = meeting("Consultations on culture and tourism");
        edit.put("status", "HELD");
        edit.put("version", created.get("version").asLong());
        mvc.perform(put("/api/meetings/" + created.get("id").asLong()).header(HttpHeaders.AUTHORIZATION, manager)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(edit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(created.get("version").asLong() + 1));

        long france = references.findByKindAndCode(ReferenceKind.COUNTRY, "FR").orElseThrow().getId();
        mvc.perform(get("/api/meetings?countryId=" + france).header(HttpHeaders.AUTHORIZATION, bearer("viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Consultations on culture and tourism"));
    }

    @Test
    void validationErrorsListTheFields() throws Exception {
        mvc.perform(post("/api/meetings").header(HttpHeaders.AUTHORIZATION, bearer("manager"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.heldOn").exists());
    }

    @Test
    void referenceOfTheWrongKindIsRejected() throws Exception {
        Map<String, Object> req = meeting("Wrong reference");
        req.put("countryId", references.findByKindAndCode(ReferenceKind.MEETING_FORMAT, "ONLINE").orElseThrow().getId());
        mvc.perform(post("/api/meetings").header(HttpHeaders.AUTHORIZATION, bearer("manager"))
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("error.invalidReference"));
    }

    @Test
    void heldMeetingsCannotBeDeleted() throws Exception {
        JsonNode held = body(mvc.perform(get("/api/meetings?status=HELD").header(HttpHeaders.AUTHORIZATION, bearer("viewer"))))
                .get("content").get(0);
        mvc.perform(delete("/api/meetings/" + held.get("id").asLong()).header(HttpHeaders.AUTHORIZATION, bearer("manager")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.meetingHeldNotDeletable"));
    }

    @Test
    void executorsCanReadButNotWrite() throws Exception {
        String executor = bearer("executor1");
        mvc.perform(get("/api/meetings").header(HttpHeaders.AUTHORIZATION, executor)).andExpect(status().isOk());
        mvc.perform(post("/api/meetings").header(HttpHeaders.AUTHORIZATION, executor)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(meeting("Not allowed"))))
                .andExpect(status().isForbidden());
    }
}
