package dev.derops.registry.notification;

import dev.derops.registry.config.AppProperties;
import dev.derops.registry.notification.DeadlineReminderService.RunResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs the deadline pass every morning (Tashkent time by default). Single-instance
 * deployment assumed; with several Tomcat nodes add a lock (e.g. ShedLock).
 */
@RestController
@Tag(name = "Administration: jobs")
public class DeadlineReminderJob {

    private final DeadlineReminderService service;
    private final Clock clock;
    private final ZoneId zone;

    public DeadlineReminderJob(DeadlineReminderService service, Clock clock, AppProperties props) {
        this.service = service;
        this.clock = clock;
        this.zone = ZoneId.of(props.notifications().zone());
    }

    @Scheduled(cron = "${app.notifications.cron}", zone = "${app.notifications.zone}")
    public void scheduled() {
        service.run(LocalDate.now(clock.withZone(zone)));
    }

    @PostMapping("/api/admin/jobs/deadline-reminders")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    @Operation(summary = "Run the daily deadline pass now (idempotent)")
    public RunResult runNow() {
        return service.run(LocalDate.now(clock.withZone(zone)));
    }
}
