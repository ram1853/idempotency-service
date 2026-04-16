package com.idempotency.service.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.idempotency.service.business.CacheManager;
import com.idempotency.service.common.dto.CacheResponse;
import com.idempotency.service.common.exception.CacheException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class ApiGwHandlerTest {

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private ApiGwHandler apiGwHandler;

    private static List<String> resources(){
        return List.of("/idempotency/reserve", "/idempotency/complete");
    }

    private static List<APIGatewayProxyRequestEvent> invalidRequests(){
        APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent1 = new APIGatewayProxyRequestEvent();
        apiGatewayProxyRequestEvent1.setHeaders(Map.of("Idempotency-Key", "123"));
        apiGatewayProxyRequestEvent1.setResource("/invalid/resource");
        apiGatewayProxyRequestEvent1.setBody("test-body");

        APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent2 = new APIGatewayProxyRequestEvent();
        apiGatewayProxyRequestEvent2.setResource("/idempotency/reserve");
        apiGatewayProxyRequestEvent2.setBody("test-body");

        APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent3 = new APIGatewayProxyRequestEvent();
        apiGatewayProxyRequestEvent3.setHeaders(Map.of("Idempotency-Key", "123"));
        apiGatewayProxyRequestEvent3.setResource("/resource/complete");

        return List.of(apiGatewayProxyRequestEvent1, apiGatewayProxyRequestEvent2, apiGatewayProxyRequestEvent3);
    }

    @ParameterizedTest
    @MethodSource("resources")
    void apply_withValidRequest_shouldReturnOkResponse(String resource){
        //Arrange
        APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent = new APIGatewayProxyRequestEvent();
        apiGatewayProxyRequestEvent.setHeaders(Map.of("Idempotency-Key", "123"));
        apiGatewayProxyRequestEvent.setResource(resource);
        apiGatewayProxyRequestEvent.setBody("test-body");
        lenient().when(cacheManager.reserveRequest(Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(new CacheResponse("123", "PROCEED")));
        lenient().when(cacheManager.completeRequest(Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(new CacheResponse("123", "COMPLETED")));

        //Act
        Mono<APIGatewayProxyResponseEvent> apiGatewayProxyResponseEventMono = apiGwHandler.apply(apiGatewayProxyRequestEvent);

        //Assert
        apiGatewayProxyResponseEventMono.subscribe((apiGatewayProxyResponseEvent -> {
            assertThat(apiGatewayProxyResponseEvent.getStatusCode(), is(HttpStatus.OK.value()));
        }));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void apply_withInvalidRequest_shouldReturnErrorResponse(APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent){
        //Act
        Mono<APIGatewayProxyResponseEvent> apiGatewayProxyResponseEventMono = apiGwHandler.apply(apiGatewayProxyRequestEvent);

        //Assert
        apiGatewayProxyResponseEventMono.subscribe((apiGatewayProxyResponseEvent -> {
            assertThat(apiGatewayProxyResponseEvent.getStatusCode(), is(HttpStatus.BAD_REQUEST.value()));
        }));
    }

    @Test
    void apply_withCachingError_shouldReturnErrorResponse(){
        //Arrange
        APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent = new APIGatewayProxyRequestEvent();
        apiGatewayProxyRequestEvent.setHeaders(Map.of("Idempotency-Key", "123"));
        apiGatewayProxyRequestEvent.setResource("/idempotency/reserve");
        apiGatewayProxyRequestEvent.setBody("test-body");
        Mockito.when(cacheManager.reserveRequest(Mockito.any(), Mockito.any()))
                .thenReturn(Mono.error(new CacheException("Error updating cache")));

        //Act
        Mono<APIGatewayProxyResponseEvent> apiGatewayProxyResponseEventMono = apiGwHandler.apply(apiGatewayProxyRequestEvent);

        //Assert
        apiGatewayProxyResponseEventMono.subscribe((apiGatewayProxyResponseEvent -> {
            assertThat(apiGatewayProxyResponseEvent.getStatusCode(), is(HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }));
    }
}