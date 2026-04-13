package com.idempotency.service.business;

import com.idempotency.service.common.dto.CacheResponse;
import com.idempotency.service.common.exception.CacheException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CacheManager {

    private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    @Autowired
    public CacheManager(ReactiveStringRedisTemplate reactiveStringRedisTemplate) {
        this.reactiveStringRedisTemplate = reactiveStringRedisTemplate;
    }

    public Mono<CacheResponse> updateCache(String key) {
        log.info("In CacheManager, received key: {}", key);
        return reactiveStringRedisTemplate.opsForValue()
                .setIfAbsent(key, "IN_PROGRESS")
                .flatMap(set -> {
                    if (Boolean.TRUE.equals(set)) {
                        log.info("Key: {} not present, created with IN_PROGRESS", key);
                        return Mono.just(new CacheResponse(key, "PROCEED"));
                    } else {
                        log.info("Key: {} exists, returning cached value", key);
                        return reactiveStringRedisTemplate.opsForValue().get(key)
                                .map(value -> new CacheResponse(key, value));
                    }
                })
                .onErrorResume(ex -> {
                    log.error("Error updating cache", ex);
                    return Mono.error(() -> new CacheException("Error updating cache for key: "+key));
                });
    }
}
