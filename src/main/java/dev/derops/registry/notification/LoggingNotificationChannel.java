package dev.derops.registry.notification;

import dev.derops.registry.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoggingNotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationChannel.class);

    @Bean
    @ConditionalOnMissingBean(NotificationChannel.class)
    NotificationChannel loggingChannel() {
        return (AppUser recipient, String text) -> log.info("Notification for {}: {}", recipient.getUsername(), text);
    }
}
