package com.deliver.shop.application.contracts.dish;

import com.deliver.shop.domain.entities.DishEntity;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

public record DishResponse(
        UUID id,
        String name,
        String description,
        String structure,
        BigDecimal price,
        String imageUrl,
        @JsonProperty("isAvailable") boolean isAvailable,
        UUID categoryId
) {
    public static DishResponse from(DishEntity dish) {
        return new DishResponse(
                dish.getId(),
                dish.getName(),
                dish.getDescription(),
                dish.getStructure(),
                dish.getPrice(),
                dish.getImageUrl(),
                dish.isAvailable(),
                dish.getCategory().getId());
    }
}
