package com.deliver.shop.application.contracts.orders;

import com.deliver.shop.domain.entities.OrderItemEntity;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(UUID dishId, String dishName, int quantity, BigDecimal priceAtOrder) {

    public static OrderItemResponse from(OrderItemEntity item) {
        return new OrderItemResponse(
                item.getDish().getId(), item.getDish().getName(), item.getQuantity(), item.getPriceAtOrder());
    }
}
