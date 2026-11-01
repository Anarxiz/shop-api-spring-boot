package com.deliver.shop.application.contracts.orders;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OrderItemDto(
        @NotNull(message = "Не указано блюдо")
        UUID dishId,

        @Min(value = 1, message = "Количество должно быть больше 0")
        int quantity
) {
}
