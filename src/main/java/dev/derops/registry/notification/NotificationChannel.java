package dev.derops.registry.notification;

import dev.derops.registry.user.AppUser;

/**
 * Outbound delivery in addition to the in-app inbox (e-mail, SMS, a messenger bot).
 * The default implementation only logs; a real channel is a drop-in bean.
 */
public interface NotificationChannel {

    void deliver(AppUser recipient, String text);
}
