package dev.derops.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AdminApiTest extends IntegrationTest {

    @Test
    void adminCreatesUsersAndDuplicatesAreRejected() throws Exception {
        String admin = bearer("admin");
        Map<String, Object> req = new HashMap<>();
        req.put("username", "new.executor");
        req.put("fullName", "New Executor");
        req.put("password", "Strong-pass-123");
        req.put("roles", List.of("EXECUTOR"));
        mvc.perform(post("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("EXECUTOR"))
                .andExpect(jsonPath("$.active").value(true));

        mvc.perform(post("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.duplicateUsername"));

        // The new account can log in straight away.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", "new.executor", "password", "Strong-pass-123"))))
                .andExpect(status().isOk());
    }

    @Test
    void weakPasswordsAndUnknownRolesAreRejected() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("username", "weak.user");
        req.put("fullName", "Weak User");
        req.put("password", "short");
        req.put("roles", List.of("NO_SUCH_ROLE"));
        mvc.perform(post("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void onlyAdminsManageUsers() throws Exception {
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer("manager")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users?q=exec").header(HttpHeaders.AUTHORIZATION, bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void permissionChangesApplyToLoggedInUsersImmediately() throws Exception {
        String viewer = bearer("viewer");
        mvc.perform(get("/api/reports/agreements").header(HttpHeaders.AUTHORIZATION, viewer)).andExpect(status().isOk());

        String admin = bearer("admin");
        JsonNode roles = body(mvc.perform(get("/api/admin/roles").header(HttpHeaders.AUTHORIZATION, admin)));
        JsonNode viewerRole = null;
        for (JsonNode r : roles) {
            if (r.get("code").asText().equals("VIEWER")) {
                viewerRole = r;
            }
        }
        assertThat(viewerRole).isNotNull();
        Map<String, Object> update = new HashMap<>();
        update.put("code", "VIEWER");
        update.put("name", "Viewer");
        update.put("permissions", List.of("MEETING_READ", "AGREEMENT_READ"));
        mvc.perform(put("/api/admin/roles/" + viewerRole.get("id").asLong()).header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(update)))
                .andExpect(status().isOk());

        // Same token as before: export is now refused without logging in again.
        mvc.perform(get("/api/reports/agreements").header(HttpHeaders.AUTHORIZATION, viewer)).andExpect(status().isForbidden());
    }

    @Test
    void referenceBooksAreLocalizedAndAdminEditable() throws Exception {
        mvc.perform(get("/api/references/COUNTRY").header(HttpHeaders.AUTHORIZATION, bearer("viewer"))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "uz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='KZ')].name").value("Qozogʻiston"));

        String admin = bearer("admin");
        Map<String, Object> req = Map.of("code", "IT", "nameUz", "Italiya", "nameRu", "Италия", "nameEn", "Italy");
        mvc.perform(post("/api/references/COUNTRY").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(req)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/references/COUNTRY").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(req)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/references/COUNTRY").header(HttpHeaders.AUTHORIZATION, bearer("manager"))
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("code", "ES", "nameUz", "Ispaniya",
                                "nameRu", "Испания", "nameEn", "Spain"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditTrailIsSearchableByAuditorsOnly() throws Exception {
        mvc.perform(get("/api/audit?action=LOGIN_SUCCESS").header(HttpHeaders.AUTHORIZATION, bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("LOGIN_SUCCESS"));
        mvc.perform(get("/api/audit").header(HttpHeaders.AUTHORIZATION, bearer("manager")))
                .andExpect(status().isForbidden());
    }
}
