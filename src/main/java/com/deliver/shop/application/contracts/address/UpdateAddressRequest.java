package com.deliver.shop.application.contracts.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAddressRequest(
        @NotBlank(message = "Город обязателен")
        @Size(max = 50, message = "Город не длиннее 50 символов")
        @Pattern(regexp = "^[А-Яа-яЁё.,\\- ]+$", message = "Город должен содержать только русские буквы")
        String city,

        @NotBlank(message = "Улица обязательна")
        @Size(max = 100, message = "Улица не длиннее 100 символов")
        @Pattern(regexp = "^[А-Яа-яЁё.,\\- ]+$", message = "Улица должна содержать только русские буквы")
        String street,

        @NotBlank(message = "Номер дома обязателен")
        @Size(max = 50, message = "Номер дома не длиннее 50 символов")
        String house,

        @NotBlank(message = "Номер квартиры обязателен")
        @Size(max = 5, message = "Номер квартиры не длиннее 5 символов")
        String apartment,

        @Size(max = 3, message = "Этаж не длиннее 3 символов")
        String floor
) {
}
