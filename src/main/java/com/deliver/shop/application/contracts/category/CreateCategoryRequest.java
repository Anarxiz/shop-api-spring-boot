package com.deliver.shop.application.contracts.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank(message = "Название обязательно")
        @Size(max = 40, message = "Название не длиннее 40 символов")
        @Pattern(regexp = "^[А-Яа-яЁё\\- ]+$", message = "Название должно содержать только русские буквы")
        String name
) {
}
