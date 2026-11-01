package com.deliver.shop.infrastructure.notifications;

import com.deliver.shop.application.events.OrderStatusChangedEvent;
import com.deliver.shop.application.services.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Обрабатывает событие в отдельном потоке
 * и только после того, как транзакция смены статуса успешно закоммичена.
 */
@Component
@RequiredArgsConstructor
public class OrderNotificationListener {

    private final NotificationDispatcher dispatcher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        dispatcher.dispatch(event.userId(), event.message());
    }
}
