package com.idempotency.service.common.dto;

public record CacheResponse(String cacheKey, String response) {
}
