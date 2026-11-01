package com.deliver.shop.application.exceptions;

/** Запрошенная сущность не найдена (HTTP 404). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
