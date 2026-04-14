package com.idempotency.service.business;

import com.idempotency.service.common.dto.CacheResponse;
import com.idempotency.service.common.exception.BadRequestException;
import com.idempotency.service.common.exception.CacheException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Service
@Slf4j
public class CacheManager {

    @Value("${cache_ttl_seconds}")
    private String cacheTtlSeconds;

    private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;
    private final RedisScript<String> reserveScript;
    private final RedisScript<String> completeScript;

    @Autowired
    public CacheManager(ReactiveStringRedisTemplate reactiveStringRedisTemplate,
                        RedisScript<String> reserveScript, RedisScript<String> completeScript) {
        this.reactiveStringRedisTemplate = reactiveStringRedisTemplate;
        this.reserveScript = reserveScript;
        this.completeScript = completeScript;
    }

    public Mono<CacheResponse> reserveRequest(String key, String bodyHash) {
        log.info("In CacheManager reserveRequest, received key: {}", key);
        return reactiveStringRedisTemplate.execute(
                        reserveScript,
                        Collections.singletonList(key),
                        bodyHash,
                        String.valueOf(cacheTtlSeconds)
                )
                .next()
                .flatMap(result -> {
                    switch (result) {
                        case "PROCEED":
                            return Mono.just(new CacheResponse(key, "PROCEED"));

                        case "IN_PROGRESS":
                            return Mono.just(new CacheResponse(key, "IN_PROGRESS"));

                        case "COMPLETED":
                            return Mono.just(new CacheResponse(key, "COMPLETED"));

                        case "HASH_MISMATCH":
                            return Mono.error(new BadRequestException("Same Idempotency-Key with different body received"));

                        default:
                            return Mono.error(new BadRequestException("Unexpected result: " + result));
                    }
                })
                .onErrorMap(ex -> {
                    log.error("Error while complete request", ex);
                    if(ex instanceof BadRequestException) return ex;
                    return new CacheException("Error while complete request: "+ex.getMessage());
                });
    }

    public Mono<CacheResponse> completeRequest(String key, String bodyHash) {
        log.info("In CacheManager completeRequest, received key: {}", key);
        return reactiveStringRedisTemplate.execute(
                        completeScript,
                        Collections.singletonList(key),
                        bodyHash,
                        String.valueOf(cacheTtlSeconds)
                )
                .next()
                .flatMap(result -> {
                    switch (result) {
                        case "COMPLETED":
                            return Mono.just(new CacheResponse(key, "COMPLETED"));

                        case "NOT_FOUND":
                            return Mono.error(new BadRequestException("Key not found, Either reserve not called or key expired"));

                        case "HASH_MISMATCH":
                            return Mono.error(new BadRequestException("Same Idempotency-Key with different body received"));

                        default:
                            return Mono.error(new BadRequestException("Unexpected result: " + result));
                    }
                })
                .onErrorMap(ex -> {
                    // Lua errors come as exceptions
                    log.error("Error while complete request", ex);
                    if(ex instanceof BadRequestException) return ex;
                    return new CacheException("Error while complete request: "+ex.getMessage());
                });
    }
}
