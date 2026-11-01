package com.deliver.shop.application.exceptions;

/** Неверные учётные данные (HTTP 401). */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
