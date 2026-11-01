package com.deliver.shop.application.services;

import com.deliver.shop.application.interfaces.notifications.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Рассылает сообщение по всем каналам; сбой одного канала не мешает остальным. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final List<NotificationChannel> channels;

    public void dispatch(UUID userId, String message) {
        for (NotificationChannel channel : channels) {
            try {
                channel.send(userId, message);
            } catch (Exception ex) {
                log.error("Канал {} не смог отправить уведомление пользователю {}", channel.name(), userId, ex);
            }
        }
    }
}
