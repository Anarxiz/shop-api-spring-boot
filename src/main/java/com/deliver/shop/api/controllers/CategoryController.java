package com.deliver.shop.api.controllers;

import com.deliver.shop.application.contracts.category.CategoryResponse;
import com.deliver.shop.application.contracts.category.CreateCategoryRequest;
import com.deliver.shop.application.contracts.category.UpdateCategoryRequest;
import com.deliver.shop.application.services.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryResponse> getAll() {
        return categoryService.getAll();
    }

    @GetMapping("/{id}")
    public CategoryResponse getById(@PathVariable UUID id) {
        return categoryService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<UUID> create(@Valid @RequestBody CreateCategoryRequest request) {
        UUID id = categoryService.create(request);
        return ResponseEntity.created(URI.create("/api/categories/" + id)).body(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public void update(@PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest request) {
        categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public void delete(@PathVariable UUID id) {
        categoryService.delete(id);
    }
}
