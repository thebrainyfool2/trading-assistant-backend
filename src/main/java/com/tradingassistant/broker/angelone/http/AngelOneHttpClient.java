package com.tradingassistant.broker.angelone.http;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tradingassistant.broker.angelone.dto.AngelOneEnvelope;
import com.tradingassistant.broker.config.AngelOneProperties;
import com.tradingassistant.broker.exception.BrokerAuthenticationException;
import com.tradingassistant.broker.exception.BrokerError;
import com.tradingassistant.broker.exception.BrokerException;
import com.tradingassistant.broker.exception.BrokerInvalidResponseException;
import com.tradingassistant.broker.exception.BrokerRateLimitException;
import com.tradingassistant.broker.exception.BrokerRequestException;
import com.tradingassistant.broker.exception.BrokerSessionExpiredException;
import com.tradingassistant.broker.exception.BrokerUnavailableException;
import com.tradingassistant.common.api.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

/**
 * SmartAPI HTTP transport.
 *
 * <p>The client owns common headers, request correlation, timeout behavior,
 * bounded retries and the translation of broker responses into the application's
 * broker exceptions. Request bodies and sensitive headers are never logged.</p>
 */
public class AngelOneHttpClient {

    private static final Logger log = LoggerFactory.getLogger(AngelOneHttpClient.class);
    private static final String USER_AGENT = "TradingAssistant/0.1";

    private final AngelOneProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AngelOneHttpClient(AngelOneProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public <T> T post(String path,
                      Object requestBody,
                      ParameterizedTypeReference<T> responseType,
                      String accessToken,
                      RetryPolicy retryPolicy) {
        return execute("POST", path, retryPolicy, () -> restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> applyHeaders(headers, accessToken))
                .body(requestBody)
                .retrieve()
                .body(responseType));
    }

    public <T> T get(String path,
                     ParameterizedTypeReference<T> responseType,
                     String accessToken,
                     RetryPolicy retryPolicy) {
        return execute("GET", path, retryPolicy, () -> restClient.get()
                .uri(path)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> applyHeaders(headers, accessToken))
                .retrieve()
                .body(responseType));
    }

    public <T> T getAbsolute(URI uri,
                             ParameterizedTypeReference<T> responseType,
                             RetryPolicy retryPolicy) {
        return execute("GET", uri.getPath(), retryPolicy, () -> restClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .headers(this::applyHeaders)
                .retrieve()
                .body(responseType));
    }

    private <T> T execute(String method, String path, RetryPolicy retryPolicy, RequestOperation<T> operation) {
        int attempt = 0;
        while (true) {
            String requestId = RequestContext.currentRequestId();
            log.info("broker_http_request requestId={} method={} path={} attempt={}",
                    requestId, method, path, attempt + 1);
            try {
                T body = operation.execute();
                if (body == null) {
                    throw new BrokerInvalidResponseException("The broker returned an empty response.");
                }
                validateEnvelope(body);
                return body;
            } catch (RestClientResponseException exception) {
                BrokerFailure failure = translateHttpFailure(exception);
                if (failure.retryable() && retryPolicy.allowsRetry(attempt)) {
                    Duration delay = retryDelay(exception, retryPolicy, attempt);
                    log.warn("broker_http_retry requestId={} method={} path={} status={} code={} attempt={} delayMs={}",
                            requestId, method, path, exception.getStatusCode().value(), failure.brokerCode(),
                            attempt + 1, delay.toMillis());
                    sleep(delay);
                    attempt++;
                    continue;
                }
                log.warn("broker_http_failure requestId={} method={} path={} status={} code={} category={}",
                        requestId, method, path, exception.getStatusCode().value(), failure.brokerCode(),
                        failure.exception().error());
                throw failure.exception();
            } catch (ResourceAccessException exception) {
                if (retryPolicy.allowsRetry(attempt)) {
                    Duration delay = retryPolicy.delay(attempt);
                    log.warn("broker_http_network_retry requestId={} method={} path={} attempt={} delayMs={}",
                            requestId, method, path, attempt + 1, delay.toMillis());
                    sleep(delay);
                    attempt++;
                    continue;
                }
                log.warn("broker_http_network_failure requestId={} method={} path={}",
                        requestId, method, path);
                throw new BrokerUnavailableException("The broker could not be reached.", exception);
            } catch (RestClientException exception) {
                log.warn("broker_http_invalid_response requestId={} method={} path={}",
                        requestId, method, path);
                throw new BrokerInvalidResponseException("The broker response could not be read.", exception);
            }
        }
    }

    private void validateEnvelope(Object body) {
        if (body instanceof AngelOneEnvelope<?> envelope && !envelope.status()) {
            String code = safeCode(envelope.errorcode());
            String message = envelope.message() == null ? "" : envelope.message().toLowerCase(Locale.ROOT);
            if (looksLikeTotp(message)) {
                throw new BrokerAuthenticationException(BrokerError.INVALID_TOTP, "Invalid TOTP.", code);
            }
            if (isSessionCode(code)) {
                throw new BrokerSessionExpiredException("The broker session is no longer valid.", code);
            }
            if (isAuthenticationCode(code)) {
                throw new BrokerAuthenticationException(BrokerError.AUTHENTICATION_FAILED,
                        "The broker rejected the configured credentials.", code);
            }
            throw new BrokerRequestException("The broker rejected the request.", code);
        }
    }

    private BrokerFailure translateHttpFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        BrokerResponseError responseError = readError(exception.getResponseBodyAsString());
        String code = responseError.code();
        String message = responseError.message().toLowerCase(Locale.ROOT);

        if (status == 429 || isRateLimitMessage(status, message)) {
            return new BrokerFailure(new BrokerRateLimitException(
                    "The broker rate limit was exceeded.", retryAfter(exception), code),
                    true, code);
        }
        if (status == 401 || status == 403) {
            if (isSessionCode(code)) {
                return new BrokerFailure(new BrokerSessionExpiredException(
                        "The broker session is no longer valid.", code), false, code);
            }
            return new BrokerFailure(new BrokerAuthenticationException(
                    BrokerError.AUTHENTICATION_FAILED, "The broker rejected the configured credentials.", code),
                    false, code);
        }
        if (status >= 500) {
            return new BrokerFailure(new BrokerUnavailableException("The broker is temporarily unavailable."),
                    status == 500 || status == 502 || status == 503 || status == 504, code);
        }
        if (looksLikeTotp(message)) {
            return new BrokerFailure(new BrokerAuthenticationException(
                    BrokerError.INVALID_TOTP, "Invalid TOTP.", code), false, code);
        }
        if (isSessionCode(code)) {
            return new BrokerFailure(new BrokerSessionExpiredException(
                    "The broker session is no longer valid.", code), false, code);
        }
        if (isAuthenticationCode(code)) {
            return new BrokerFailure(new BrokerAuthenticationException(
                    BrokerError.AUTHENTICATION_FAILED, "The broker rejected the configured credentials.", code),
                    false, code);
        }
        return new BrokerFailure(new BrokerRequestException("The broker rejected the request.", code),
                false, code);
    }

    private BrokerResponseError readError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return new BrokerResponseError(null, "");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return new BrokerResponseError(
                    safeCode(text(root, "errorcode")),
                    text(root, "message"));
        } catch (Exception exception) {
            return new BrokerResponseError(null, "");
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private Duration retryAfter(RestClientResponseException exception) {
        HttpHeaders headers = exception.getResponseHeaders();
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Duration retryDelay(RestClientResponseException exception, RetryPolicy policy, int attempt) {
        Duration retryAfter = retryAfter(exception);
        return retryAfter == null ? policy.delay(attempt) : retryAfter;
    }

    private static boolean isSessionCode(String code) {
        return "AG8001".equals(code) || "AG8002".equals(code) || "AG8003".equals(code)
                || "AB8050".equals(code) || "AB8051".equals(code);
    }

    private static boolean isAuthenticationCode(String code) {
        return "AB1000".equals(code) || "AB1001".equals(code) || "AB1002".equals(code)
                || "AB1006".equals(code) || "AB1032".equals(code) || "AB7001".equals(code);
    }

    private static boolean looksLikeTotp(String message) {
        return message.contains("totp") || message.contains("otp");
    }

    private static boolean isRateLimitMessage(int status, String message) {
        return status == 403 && (message.contains("rate limit") || message.contains("access rate"));
    }

    private static String safeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String trimmed = code.trim();
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
    }

    private void applyHeaders(HttpHeaders headers, String accessToken) {
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set("X-UserType", "USER");
        headers.set("X-SourceID", "WEB");
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            headers.set("X-PrivateKey", properties.apiKey());
        }
        if (accessToken != null && !accessToken.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        if (properties.clientLocalIp() != null && !properties.clientLocalIp().isBlank()) {
            headers.set("X-ClientLocalIP", properties.clientLocalIp());
        }
        if (properties.clientPublicIp() != null && !properties.clientPublicIp().isBlank()) {
            headers.set("X-ClientPublicIP", properties.clientPublicIp());
        }
        if (properties.macAddress() != null && !properties.macAddress().isBlank()) {
            headers.set("X-MACAddress", properties.macAddress());
        }
    }

    private void applyHeaders(HttpHeaders headers) {
        applyHeaders(headers, null);
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BrokerUnavailableException("Broker request was interrupted.", exception);
        }
    }

    @FunctionalInterface
    private interface RequestOperation<T> {
        T execute();
    }

    private record BrokerResponseError(String code, String message) {
    }

    private record BrokerFailure(BrokerException exception, boolean retryable, String brokerCode) {
    }
}
