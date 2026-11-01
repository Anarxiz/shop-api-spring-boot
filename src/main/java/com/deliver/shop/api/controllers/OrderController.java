package com.deliver.shop.api.controllers;

import com.deliver.shop.api.security.CurrentUser;
import com.deliver.shop.application.contracts.orders.CreateOrderRequest;
import com.deliver.shop.application.contracts.orders.OrderResponse;
import com.deliver.shop.application.contracts.orders.UpdateOrderStatusRequest;
import com.deliver.shop.application.services.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<UUID> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateOrderRequest request) {
        UUID id = orderService.create(CurrentUser.id(jwt), request);
        return ResponseEntity.created(URI.create("/api/orders/" + id)).body(id);
    }

    @GetMapping
    public List<OrderResponse> getMyOrders(@AuthenticationPrincipal Jwt jwt) {
        return orderService.getByUser(CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return orderService.getById(id, CurrentUser.id(jwt));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public void updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        orderService.updateStatus(id, request);
    }
}
