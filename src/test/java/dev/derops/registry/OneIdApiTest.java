package dev.derops.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** OneID SSO flow against the mock OneID adapter (app.oneid.mode=mock). */
class OneIdApiTest extends IntegrationTest {

    private String state() throws Exception {
        JsonNode start = body(mvc.perform(get("/api/auth/oneid/authorize")).andExpect(status().isOk()));
        assertThat(start.get("url").asText()).contains("state=");
        return start.get("state").asText();
    }

    private ResultActions callback(String code, String state) throws Exception {
        return mvc.perform(post("/api/auth/oneid/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("code", code, "state", state))));
    }

    @Test
    void linkedIdentityLogsIn() throws Exception {
        callback("mock:demo-0003", state())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("executor1"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void unlinkedIdentityIsRefusedNotProvisioned() throws Exception {
        callback("mock:someone-else", state())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("error.oneidNotLinked"));
    }

    @Test
    void forgedStateIsRejected() throws Exception {
        callback("mock:demo-0003", "forged")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("error.oneidState"));
    }

    @Test
    void stateTokenCannotBeUsedAsAnAccessToken() throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + state()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessTokenCannotBeUsedAsState() throws Exception {
        String access = bearer("admin").substring("Bearer ".length());
        callback("mock:demo-0001", access).andExpect(status().isBadRequest());
    }
}
