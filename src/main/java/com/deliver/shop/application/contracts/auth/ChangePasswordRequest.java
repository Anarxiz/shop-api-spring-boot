package com.deliver.shop.application.contracts.auth;

import jakarta.validation.constraints.*;

public record ChangePasswordRequest(
        @NotBlank(message = "Текущий пароль обязателен")
        String currentPassword,

        @NotBlank(message = "Новый пароль обязателен")
        @Size(min = 8, max = 100, message = "Пароль должен быть от 8 до 100 символов")
        @Pattern(regexp = ".*[A-Z].*", message = "Пароль должен содержать хотя бы одну заглавную букву")
        @Pattern(regexp = ".*[a-z].*", message = "Пароль должен содержать хотя бы одну строчную букву")
        @Pattern(regexp = ".*\\d.*", message = "Пароль должен содержать хотя бы одну цифру")
        @Pattern(regexp = ".*[^\\w\\s].*", message = "Пароль должен содержать хотя бы один спецсимвол")
        @Pattern(regexp = "^\\S*$", message = "Пароль не должен содержать пробелов")
        String newPassword
) {
}
