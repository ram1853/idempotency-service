package com.idempotency.service.common.exception;

public class HashException extends RuntimeException{

    public HashException(String message) {
        super(message);
    }
}
