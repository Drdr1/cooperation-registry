package dev.derops.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.derops.registry.notification.DeadlineReminderService;
import dev.derops.registry.notification.DeadlineReminderService.RunResult;
import dev.derops.registry.notification.NotificationRepository;
import dev.derops.registry.notification.NotificationType;
import dev.derops.registry.user.UserRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class DeadlineReminderTest extends IntegrationTest {

    @Autowired
    DeadlineReminderService reminders;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    UserRepository users;

    @Test
    void remindsAboutDueAndOverdueItemsOncePerPeriod() throws Exception {
        LocalDate today = LocalDate.now();
        RunResult first = reminders.run(today);
        assertThat(first.dueSoon()).isGreaterThanOrEqualTo(1);
        assertThat(first.overdue()).isGreaterThanOrEqualTo(1);

        long executor1 = users.findWithRolesByUsername("executor1").orElseThrow().getId();
        assertThat(notifications.findByUserIdAndType(executor1, NotificationType.ITEM_DUE_SOON)).hasSize(1);
        assertThat(notifications.findByUserIdAndType(executor1, NotificationType.ITEM_OVERDUE)).hasSize(1);

        RunResult second = reminders.run(today);
        assertThat(second.dueSoon()).isZero();
        assertThat(second.overdue()).isZero();

        JsonNode inbox = body(mvc.perform(get("/api/notifications?unreadOnly=true")
                .header(HttpHeaders.AUTHORIZATION, bearer("executor1"))
                .header(HttpHeaders.ACCEPT_LANGUAGE, "ru")));
        assertThat(inbox.toString()).contains("Просрочен пункт").contains("Приближается срок");
    }

    @Test
    void agreementsPastTheirEndDateExpireAutomatically() throws Exception {
        String manager = bearer("manager");
        String admin = bearer("admin");
        Map<String, Object> req = new HashMap<>();
        req.put("registrationNumber", "T-EXP-1");
        req.put("title", "Short-lived agreement");
        req.put("signedOn", LocalDate.now().minusDays(30).toString());
        req.put("validUntil", LocalDate.now().minusDays(1).toString());
        long id = body(mvc.perform(post("/api/agreements").header(HttpHeaders.AUTHORIZATION, manager)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(req)))
                .andExpect(status().isCreated())).get("id").asLong();
        for (String[] step : new String[][] {{manager, "UNDER_REVIEW"}, {admin, "SIGNED"}, {manager, "IN_FORCE"}}) {
            mvc.perform(post("/api/agreements/" + id + "/status").header(HttpHeaders.AUTHORIZATION, step[0])
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("status", step[1]))))
                    .andExpect(status().isOk());
        }

        RunResult result = reminders.run(LocalDate.now());
        assertThat(result.expired()).isEqualTo(1);

        JsonNode detail = body(mvc.perform(get("/api/agreements/" + id).header(HttpHeaders.AUTHORIZATION, manager)));
        assertThat(detail.get("agreement").get("status").asText()).isEqualTo("EXPIRED");
        JsonNode history = detail.get("history");
        assertThat(history.get(history.size() - 1).get("changedBy").asText()).isEqualTo("system");
    }

    @Test
    void adminCanTriggerTheRunManually() throws Exception {
        mvc.perform(post("/api/admin/jobs/deadline-reminders").header(HttpHeaders.AUTHORIZATION, bearer("admin")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/jobs/deadline-reminders").header(HttpHeaders.AUTHORIZATION, bearer("manager")))
                .andExpect(status().isForbidden());
    }
}
