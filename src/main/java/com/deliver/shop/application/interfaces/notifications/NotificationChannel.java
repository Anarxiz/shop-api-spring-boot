package com.deliver.shop.application.interfaces.notifications;

import java.util.UUID;

/** Канал доставки уведомлений (email, push, ...). Каждая реализация — отдельный бин. */
public interface NotificationChannel {

    String name();

    void send(UUID userId, String message) throws Exception;
}
