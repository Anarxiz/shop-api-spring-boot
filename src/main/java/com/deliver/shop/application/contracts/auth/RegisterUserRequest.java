package com.deliver.shop.application.contracts.auth;

import jakarta.validation.constraints.*;

public record RegisterUserRequest(
        @NotBlank(message = "Имя обязательно")
        @Size(max = 20, message = "Имя не длиннее 20 символов")
        @Pattern(regexp = "^[А-Яа-яЁё]+$", message = "Имя должно содержать только русские буквы")
        String firstName,

        @Size(max = 20, message = "Фамилия не длиннее 20 символов")
        @Pattern(regexp = "^[А-Яа-яЁё]*$", message = "Фамилия должна содержать только русские буквы")
        String lastName,

        @NotBlank(message = "Email обязателен")
        @Email(message = "Некорректный формат email")
        @Size(max = 100, message = "Email не длиннее 100 символов")
        String email,

        @NotBlank(message = "Телефон обязателен")
        @Pattern(regexp = "^\\+7\\d{10}$", message = "Телефон должен быть в формате +7XXXXXXXXXX")
        String phone,

        @NotBlank(message = "Пароль обязателен")
        @Size(min = 8, max = 100, message = "Пароль должен быть от 8 до 100 символов")
        @Pattern(regexp = ".*[A-Z].*", message = "Пароль должен содержать хотя бы одну заглавную букву")
        @Pattern(regexp = ".*[a-z].*", message = "Пароль должен содержать хотя бы одну строчную букву")
        @Pattern(regexp = ".*\\d.*", message = "Пароль должен содержать хотя бы одну цифру")
        @Pattern(regexp = ".*[^\\w\\s].*", message = "Пароль должен содержать хотя бы один спецсимвол")
        @Pattern(regexp = "^\\S*$", message = "Пароль не должен содержать пробелов")
        String password
) {
}
