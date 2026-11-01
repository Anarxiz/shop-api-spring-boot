package com.deliver.shop.infrastructure.notifications;

import com.deliver.shop.application.interfaces.notifications.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Заглушка push-уведомлений. */
@Slf4j
@Component
public class PushNotificationChannel implements NotificationChannel {

    @Override
    public String name() {
        return "push";
    }

    @Override
    public void send(UUID userId, String message) {
        log.debug("Push для {}: {}", userId, message);
    }
}
