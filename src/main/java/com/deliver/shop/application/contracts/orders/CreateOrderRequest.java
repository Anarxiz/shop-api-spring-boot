package com.deliver.shop.application.contracts.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull(message = "Нужно указать адрес доставки")
        UUID addressId,

        @NotEmpty(message = "Заказ должен содержать хотя бы одну позицию")
        @Valid
        List<OrderItemDto> items
) {
}
