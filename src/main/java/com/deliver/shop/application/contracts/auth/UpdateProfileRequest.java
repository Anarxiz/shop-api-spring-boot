package com.deliver.shop.application.contracts.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Имя обязательно")
        @Size(max = 20, message = "Имя не длиннее 20 символов")
        @Pattern(regexp = "^[А-Яа-яЁё]+$", message = "Имя должно содержать только русские буквы")
        String firstName,

        @Size(max = 20, message = "Фамилия не длиннее 20 символов")
        @Pattern(regexp = "^[А-Яа-яЁё]*$", message = "Фамилия должна содержать только русские буквы")
        String lastName,

        @NotBlank(message = "Телефон обязателен")
        @Pattern(regexp = "^\\+7\\d{10}$", message = "Телефон должен быть в формате +7XXXXXXXXXX")
        String phone
) {
}
