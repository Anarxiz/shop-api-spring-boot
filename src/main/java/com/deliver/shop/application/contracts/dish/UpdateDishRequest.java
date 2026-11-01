package com.deliver.shop.application.contracts.dish;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateDishRequest(
        @NotBlank(message = "Имя обязательно")
        @Size(max = 40, message = "Не более 40 символов")
        String name,

        @NotBlank(message = "Описание обязательно")
        @Size(max = 200, message = "Не более 200 символов")
        String description,

        @NotBlank(message = "Состав обязателен")
        @Size(max = 200, message = "Не более 200 символов")
        String structure,

        @NotNull(message = "Цена обязательна")
        @DecimalMin(value = "0", inclusive = false, message = "Цена не может быть меньше или равной 0")
        @Digits(integer = 16, fraction = 2, message = "Некорректная цена")
        BigDecimal price,

        @NotNull(message = "Нужно указать категорию")
        UUID categoryId,

        @Pattern(regexp = "^https?://\\S+$", message = "Некорректный URL изображения")
        String imageUrl,

        @JsonProperty("isAvailable") boolean isAvailable
) {
}
