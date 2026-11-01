package com.deliver.shop.application.contracts.orders;

import com.deliver.shop.domain.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull(message = "Некорректный статус заказа")
        OrderStatus status
) {
}
