package com.idempotency.service.business;

import com.idempotency.service.common.dto.CacheResponse;
import com.idempotency.service.common.exception.BadRequestException;
import com.idempotency.service.common.exception.CacheException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ExtendWith(MockitoExtension.class)
class CacheManagerTest {

    @Mock
    private ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    @Mock
    private RedisScript<String> reserveScript;

    @Mock
    private RedisScript<String> completeScript;

    @InjectMocks
    private CacheManager cacheManager;

    private static List<String> states(){
        return List.of("PROCEED", "IN_PROGRESS", "COMPLETED");
    }

    @ParameterizedTest
    @MethodSource("states")
    void reserveRequest_withDifferentStates_shouldRespondWithCorrectState(String state){
        //Arrange
        Mockito.when(reactiveStringRedisTemplate.execute(Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any())).thenReturn(Flux.just(state));

        //Act
        Mono<CacheResponse> cacheResponseMono = cacheManager.reserveRequest("123", "test-hash");

        //Assert
        cacheResponseMono.subscribe((cacheResponse -> {
            assertThat(cacheResponse.response(), is(state));
        }));
    }

    private static List<String> errors(){
        return List.of("HASH_MISMATCH", "INVALID_STATE");
    }

    @ParameterizedTest
    @MethodSource("errors")
    void reserveRequest_withClientErrors_shouldThrowException(String state) {
        //Arrange
        Mockito.when(reactiveStringRedisTemplate.execute(Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any())).thenReturn(Flux.just(state));

        //Act and assert
        StepVerifier.create(cacheManager.reserveRequest("123", "test-hash"))
                .expectError(BadRequestException.class)
                .verify();
    }

    @Test
    void reserveRequest_withRuntimeErrors_shouldThrowException() {
        //Arrange
        Mockito.when(reactiveStringRedisTemplate.execute(Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any())).thenReturn(Flux.error(new RuntimeException()));

        //Act and assert
        StepVerifier.create(cacheManager.reserveRequest("123", "test-hash"))
                .expectError(CacheException.class)
                .verify();
    }

    @Test
    void completeRequest_withHappyPathStates_shouldRespondWithCompletedState(){
        //Arrange
        Mockito.when(reactiveStringRedisTemplate.execute(Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any())).thenReturn(Flux.just("COMPLETED"));

        //Act
        Mono<CacheResponse> cacheResponseMono = cacheManager.completeRequest("123", "test-hash");

        //Assert
        cacheResponseMono.subscribe((cacheResponse -> {
            assertThat(cacheResponse.response(), is("COMPLETED"));
        }));
    }

    private static List<String> invalidStates() {
        return List.of("NOT_FOUND", "HASH_MISMATCH", "INVALID_STATE");
    }

    @ParameterizedTest
    @MethodSource("invalidStates")
    void completeRequest_withInvalidStates_shouldThrowException(String state){
        //Arrange
        Mockito.when(reactiveStringRedisTemplate.execute(Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any())).thenReturn(Flux.just(state));

        //Act and Assert
        StepVerifier.create(cacheManager.completeRequest("123", "test-hash"))
                .expectError(BadRequestException.class)
                .verify();
    }

    @Test
    void completeRequest_withRuntimeErrors_shouldThrowException() {
        //Arrange
        Mockito.when(reactiveStringRedisTemplate.execute(Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any())).thenReturn(Flux.error(new RuntimeException()));

        //Act and assert
        StepVerifier.create(cacheManager.completeRequest("123", "test-hash"))
                .expectError(CacheException.class)
                .verify();
    }
}