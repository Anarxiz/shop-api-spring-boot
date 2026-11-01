package com.deliver.shop.application.exceptions;

/** Нарушено бизнес-правило (HTTP 400). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
