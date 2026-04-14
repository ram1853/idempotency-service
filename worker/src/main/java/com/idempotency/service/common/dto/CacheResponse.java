package com.idempotency.service.common.dto;

public record CacheResponse(String idempotencyKey, String response) {
}
