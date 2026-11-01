package com.deliver.shop.application.contracts.orders;

import com.deliver.shop.application.contracts.address.AddressResponse;
import com.deliver.shop.domain.entities.OrderEntity;
import com.deliver.shop.domain.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        AddressResponse address,
        Instant createdAt,
        OrderStatus status,
        BigDecimal totalPrice,
        List<OrderItemResponse> items
) {
    public static OrderResponse from(OrderEntity order) {
        List<OrderItemResponse> items = order.getItems().stream().map(OrderItemResponse::from).toList();
        // сумма считается по позициям
        BigDecimal total = items.stream()
                .map(i -> i.priceAtOrder().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderResponse(
                order.getId(), AddressResponse.from(order.getAddress()),
                order.getCreatedAt(), order.getStatus(), total, items);
    }
}
