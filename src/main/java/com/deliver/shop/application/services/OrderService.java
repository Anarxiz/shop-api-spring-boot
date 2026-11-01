package com.deliver.shop.application.services;

import com.deliver.shop.application.contracts.orders.CreateOrderRequest;
import com.deliver.shop.application.contracts.orders.OrderItemDto;
import com.deliver.shop.application.contracts.orders.OrderResponse;
import com.deliver.shop.application.contracts.orders.UpdateOrderStatusRequest;
import com.deliver.shop.application.events.OrderStatusChangedEvent;
import com.deliver.shop.application.exceptions.BusinessException;
import com.deliver.shop.application.exceptions.NotFoundException;
import com.deliver.shop.application.interfaces.repository.AddressRepository;
import com.deliver.shop.application.interfaces.repository.DishRepository;
import com.deliver.shop.application.interfaces.repository.OrderRepository;
import com.deliver.shop.application.interfaces.repository.UserRepository;
import com.deliver.shop.domain.entities.AddressEntity;
import com.deliver.shop.domain.entities.DishEntity;
import com.deliver.shop.domain.entities.OrderEntity;
import com.deliver.shop.domain.entities.OrderItemEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String ORDER_NOT_FOUND = "Заказ не найден";

    private final OrderRepository orderRepository;
    private final DishRepository dishRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UUID create(UUID userId, CreateOrderRequest request) {
        AddressEntity address = addressRepository.findByIdAndUserId(request.addressId(), userId).orElseThrow(() -> {
            log.info("Не удалось создать заказ для пользователя {}: адрес не принадлежит пользователю", userId);
            return new BusinessException("Адрес " + request.addressId() + " не найден");
        });

        OrderEntity order = new OrderEntity();
        order.setUser(userRepository.getReferenceById(userId));
        order.setAddress(address);

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemDto item : request.items()) {
            DishEntity dish = dishRepository.findById(item.dishId()).orElseThrow(() -> {
                log.warn("Блюдо {} не найдено при создании заказа для пользователя {}", item.dishId(), userId);
                return new BusinessException("Блюдо " + item.dishId() + " не найдено");
            });
            if (!dish.isAvailable()) {
                log.warn("Блюдо {} недоступно при создании заказа для пользователя {}", item.dishId(), userId);
                throw new BusinessException("Блюдо " + item.dishId() + " не доступно");
            }

            OrderItemEntity orderItem = new OrderItemEntity();
            orderItem.setOrder(order);
            orderItem.setDish(dish);
            orderItem.setQuantity(item.quantity());
            orderItem.setPriceAtOrder(dish.getPrice()); // фиксируем цену на момент заказа
            order.getItems().add(orderItem);

            total = total.add(dish.getPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }
        order.setTotalPrice(total);
        orderRepository.save(order);

        log.info("Создан заказ {} для пользователя {} на сумму {}", order.getId(), userId, total);
        return order.getId();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getByUser(UUID userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id, UUID userId) {
        return orderRepository.findByIdAndUserId(id, userId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new NotFoundException(ORDER_NOT_FOUND));
    }

    @Transactional
    public void updateStatus(UUID id, UpdateOrderStatusRequest request) {
        OrderEntity order = orderRepository.findById(id).orElseThrow(() -> {
            log.warn("Не удалось обновить статус заказа {}", id);
            return new NotFoundException(ORDER_NOT_FOUND);
        });

        order.setStatus(request.status());
        log.info("Статус заказа {} изменён на {}", id, request.status());

        // уведомление уйдёт асинхронно и только после успешного коммита
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                order.getUser().getId(), "Статус заказа изменён на " + request.status()));
    }
}
