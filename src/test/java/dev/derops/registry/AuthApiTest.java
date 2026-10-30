package dev.derops.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.derops.registry.audit.AuditAction;
import dev.derops.registry.audit.AuditRepository;
import dev.derops.registry.user.AppUser;
import dev.derops.registry.user.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AuthApiTest extends IntegrationTest {

    @Autowired
    UserRepository users;

    @Autowired
    AuditRepository audit;

    private ResultActions login(String username, String password, String lang) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .header(HttpHeaders.ACCEPT_LANGUAGE, lang)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("username", username, "password", password))));
    }

    @Test
    void loginReturnsTokenWithEffectivePermissions() throws Exception {
        login("admin", PASSWORD, "en")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.user.permissions").isArray());
        JsonNode me = body(mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer("viewer")))
                .andExpect(status().isOk()));
        assertThat(me.get("permissions").toString()).contains("REPORT_EXPORT").doesNotContain("AGREEMENT_WRITE");
    }

    @Test
    void wrongPasswordIsRejectedInTheCallersLanguageAndAudited() throws Exception {
        login("manager", "wrong-password", "ru")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("error.badCredentials"))
                .andExpect(jsonPath("$.detail").value("Неверный логин или пароль"));
        login("manager", "wrong-password", "uz")
                .andExpect(jsonPath("$.detail").value("Login yoki parol notoʻgʻri"));
        assertThat(audit.findByUsernameAndActionOrderByIdDesc("manager", AuditAction.LOGIN_FAILED)).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void unknownUserGetsTheSameAnswerAsWrongPassword() throws Exception {
        login("nobody", "whatever-123", "en")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("error.badCredentials"));
    }

    @Test
    void accountLocksAfterFiveFailures() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("executor1", "wrong-password", "en").andExpect(status().isUnauthorized());
        }
        login("executor1", PASSWORD, "en")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("error.accountLocked"));
    }

    @Test
    void deactivatingAUserRevokesTheirTokenImmediately() throws Exception {
        String token = bearer("executor2");
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());

        AppUser user = users.findWithRolesByUsername("executor2").orElseThrow();
        user.setActive(false);
        users.flush();

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isUnauthorized());
    }

    @Test
    void apiRequiresAToken() throws Exception {
        mvc.perform(get("/api/agreements")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/agreements").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthAndApiDocsArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.info.title").value("Cooperation Registry API"));
    }
}
