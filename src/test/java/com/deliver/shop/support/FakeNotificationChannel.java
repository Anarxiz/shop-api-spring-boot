package com.deliver.shop.support;

import com.deliver.shop.application.interfaces.notifications.NotificationChannel;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Тестовый канал: вместо отправки запоминает сообщения, чтобы тест мог их проверить. */
public class FakeNotificationChannel implements NotificationChannel {

    public record Sent(UUID userId, String message) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public void send(UUID userId, String message) {
        sent.add(new Sent(userId, message));
    }

    public List<Sent> sent() {
        return sent;
    }
}
