package dev.derops.registry.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String defaultLocale,
        Jwt jwt,
        Security security,
        Bootstrap bootstrap,
        OneId oneid,
        ExternalRegistry externalRegistry,
        Soap soap,
        Notifications notifications) {

    public record Jwt(String secret, String issuer, Duration ttl) {}

    public record Security(int maxFailedLogins, Duration lockout) {}

    public record Bootstrap(String adminUsername, String adminPassword) {}

    public record OneId(
            String mode,
            String authorizeUrl,
            String tokenUrl,
            String userinfoUrl,
            String clientId,
            String clientSecret,
            String redirectUri,
            String scope,
            String subjectAttribute,
            String nameAttribute) {}

    public record ExternalRegistry(String mode, String url) {}

    public record Soap(String username, String password) {}

    public record Notifications(int reminderDays, int expiryWarningDays, String cron, String zone) {}
}
