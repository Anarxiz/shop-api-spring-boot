package com.deliver.shop.domain.enums;

public enum OrderStatus {
    CREATED,     // Создан / ожидает оплаты
    PAID,        // Оплачен
    COOKING,     // Готовится
    DELIVERING,  // В пути
    COMPLETED,   // Доставлен
    CANCELLED    // Отменён
}
