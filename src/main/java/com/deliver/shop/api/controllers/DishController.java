package com.deliver.shop.api.controllers;

import com.deliver.shop.application.contracts.dish.CreateDishRequest;
import com.deliver.shop.application.contracts.dish.DishResponse;
import com.deliver.shop.application.contracts.dish.UpdateDishRequest;
import com.deliver.shop.application.services.DishService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dishes")
@RequiredArgsConstructor
public class DishController {

    private final DishService dishService;

    @GetMapping
    public List<DishResponse> getAll() {
        return dishService.getAll();
    }

    @GetMapping("/by-category/{categoryId}")
    public List<DishResponse> getByCategory(@PathVariable UUID categoryId) {
        return dishService.getByCategory(categoryId);
    }

    @GetMapping("/{id}")
    public DishResponse getById(@PathVariable UUID id) {
        return dishService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<UUID> create(@Valid @RequestBody CreateDishRequest request) {
        UUID id = dishService.create(request);
        return ResponseEntity.created(URI.create("/api/dishes/" + id)).body(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public void update(@PathVariable UUID id, @Valid @RequestBody UpdateDishRequest request) {
        dishService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public void delete(@PathVariable UUID id) {
        dishService.delete(id);
    }
}
