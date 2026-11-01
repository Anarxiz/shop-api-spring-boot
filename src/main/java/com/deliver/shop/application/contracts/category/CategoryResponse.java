package com.deliver.shop.application.contracts.category;

import com.deliver.shop.application.contracts.dish.DishResponse;
import com.deliver.shop.domain.entities.CategoryEntity;

import java.util.List;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        List<DishResponse> dishes
) {
    public static CategoryResponse from(CategoryEntity category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDishes().stream().map(DishResponse::from).toList());
    }
}
