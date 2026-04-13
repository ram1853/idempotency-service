package com.idempotency.service.common.exception;

public class CacheException extends RuntimeException{

    public CacheException(String message) {
        super(message);
    }
}
