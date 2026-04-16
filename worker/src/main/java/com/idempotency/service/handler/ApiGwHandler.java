package com.idempotency.service.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idempotency.service.business.CacheManager;
import com.idempotency.service.common.exception.BadRequestException;
import com.idempotency.service.common.exception.CacheException;
import com.idempotency.service.common.exception.HashException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigInteger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.function.Function;

//Understand on Mono just vs defer -> https://www.baeldung.com/reactive-mono-just-defer-create

@Slf4j
@Component("apiGwHandler")
public class ApiGwHandler implements Function<APIGatewayProxyRequestEvent, Mono<APIGatewayProxyResponseEvent>> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CacheManager cacheManager;

    @Autowired
    public ApiGwHandler(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    //Here our spring framework internally subscribes to Mono<APIGatewayProxyResponseEvent>, which will execute the logic inside defer.
    //flatMap unwraps the Mono inside and returns a Mono
    @Override
    public Mono<APIGatewayProxyResponseEvent> apply(APIGatewayProxyRequestEvent request) {
        return Mono.defer(() -> {
                    log.info("In ApiGwHandler, received request: {}", request);

                    String resource = request.getResource();
                    Map<String, String> headers = request.getHeaders();

                    String idempotencyKey = headers.get("Idempotency-Key");
                    if (idempotencyKey == null) {
                        throw new BadRequestException("Missing Idempotency-Key header");
                    }

                    String body = request.getBody();
                    if(body == null) {
                        throw new BadRequestException("Missing Body");
                    }

                    String bodyHash = generateHashOfBody(idempotencyKey, body);

                    return switch (resource) {
                        case "/idempotency/reserve" -> cacheManager.reserveRequest(idempotencyKey, bodyHash);
                        case "/idempotency/complete" -> cacheManager.completeRequest(idempotencyKey, bodyHash);
                        default -> Mono.error(new BadRequestException("Invalid Resource Path: " + resource));
                    };
                })
                .flatMap(body -> Mono.fromCallable(() -> new APIGatewayProxyResponseEvent()
                                .withStatusCode(HttpStatus.OK.value())
                                .withBody(OBJECT_MAPPER.writeValueAsString(body))
                ))
                .onErrorResume(this::handleError);
    }

    private String generateHashOfBody(String key, String body) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = messageDigest.digest(body.getBytes(StandardCharsets.UTF_8));
            BigInteger number = new BigInteger(1, hashBytes);
            return String.format("%064x", number);
        } catch (Exception e) {
            log.error("Generating hash for body failed", e);
            throw new HashException("Generating hash for body failed. Idempotency-Key: "+key);
        }
    }

    private Mono<APIGatewayProxyResponseEvent> handleError(Throwable ex) {
        log.error("Error occurred", ex);

        if (ex instanceof BadRequestException) {
            return Mono.just(
                    new APIGatewayProxyResponseEvent()
                            .withStatusCode(HttpStatus.BAD_REQUEST.value())
                            .withBody(ex.getMessage())
            );
        }

        if (ex instanceof HashException || ex instanceof CacheException) {
            return Mono.just(
                    new APIGatewayProxyResponseEvent()
                            .withStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .withBody(ex.getMessage())
            );
        }

        return Mono.just(
                new APIGatewayProxyResponseEvent()
                        .withStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .withBody("Unexpected error")
        );
    }
}
