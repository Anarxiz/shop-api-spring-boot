package com.deliver.shop.application.events;

import java.util.UUID;

/** Событие: статус заказа изменён. Обрабатывается асинхронно после коммита транзакции. */
public record OrderStatusChangedEvent(UUID userId, String message) {
}
