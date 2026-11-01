package io.github.arbiter.client;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.github.arbiter.api.ApiHeaders;
import io.github.arbiter.api.dto.ApiErrorResponse;
import io.github.arbiter.client.config.ApiClientConfig;
import io.github.arbiter.client.http.JacksonSupport;
import io.github.arbiter.client.security.Redactor;
import io.github.arbiter.client.transport.ApiTransport;
import io.github.arbiter.client.transport.TransportRequest;
import io.github.arbiter.client.transport.TransportResponse;
import io.github.arbiter.core.exception.DomainException;
import io.github.arbiter.core.exception.NetworkException;
import io.github.arbiter.core.exception.UserAbortedException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Transport-neutral API client: JSON, token headers, retries, and
 * DomainException mapping. The transport only moves bytes.
 */
public class ApiClient implements AutoCloseable {
    private final ApiClientConfig config;
    private final ApiTransport transport;
    private final ObjectMapper mapper;
    private final BooleanSupplier verbose;
    private final BiFunction<String, Object[], String> messageFormatter;
    private final Consumer<String> logger;

    public ApiClient(ApiClientConfig config, ApiTransport transport,
                     BooleanSupplier verbose,
                     BiFunction<String, Object[], String> messageFormatter,
                     Consumer<String> logger) {
        this.config = Objects.requireNonNull(config);
        this.transport = Objects.requireNonNull(transport);
        this.verbose = Objects.requireNonNull(verbose);
        this.messageFormatter = Objects.requireNonNull(messageFormatter);
        this.logger = Objects.requireNonNull(logger);
        this.mapper = JacksonSupport.create();
    }

    public <T> T get(String path, String token, Class<T> responseType) {
        return execute(new Call("GET", path, token, null,
                token == null ? List.of() : List.of(token)), responseType);
    }

    public <T> T post(String path, Object body, Class<T> responseType,
                      List<String> sensitiveValues) {
        return execute(new Call("POST", path, null, body, sensitiveValues),
                responseType);
    }

    public <T> T post(String path, String token, Object body, Class<T> responseType,
                      List<String> sensitiveValues) {
        List<String> secrets = new java.util.ArrayList<>(sensitiveValues);
        if (token != null) {
            secrets.add(token);
        }
        return execute(new Call("POST", path, token, body, List.copyOf(secrets)),
                responseType);
    }

    public void put(String path, String token, Object body) {
        put(path, token, body, Void.class);
    }

    public <T> T put(String path, String token, Object body, Class<T> responseType) {
        return execute(new Call("PUT", path, token, body,
                token == null ? List.of() : List.of(token)), responseType);
    }

    public void delete(String path, String token) {
        execute(new Call("DELETE", path, token, null,
                token == null ? List.of() : List.of(token)), Void.class);
    }

    @Override
    public void close() {
        try {
            transport.close();
        } catch (Exception ignored) {
            // Transport cleanup is best-effort.
        }
    }

    private <T> T execute(Call call, Class<T> responseType) {
        int maxAttempts = idempotent(call.method()) ? config.maxRetries() + 1 : 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                TransportResponse response = transport.execute(toTransport(call));
                if (response.status() >= 200 && response.status() <= 299) {
                    debug("verbose.http.response", response.status());
                    if (responseType == Void.class || response.body() == null
                            || response.body().isBlank()) {
                        return null;
                    }
                    return mapper.readValue(response.body(), responseType);
                }
                DomainException error = mapError(response, call.sensitiveValues());
                if (canRetry(response.status()) && attempt < maxAttempts) {
                    debug("verbose.http.retry.status", response.status(), attempt,
                            maxAttempts);
                    backoff(attempt);
                    continue;
                }
                throw error;
            } catch (NetworkException network) {
                if (attempt < maxAttempts) {
                    debug("verbose.http.retry.network", attempt, maxAttempts);
                    backoff(attempt);
                    continue;
                }
                throw network;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new UserAbortedException();
            } catch (JacksonException malformed) {
                throw ApiExceptionMapper.mapStatus(0, null, null);
            } catch (IOException failure) {
                if (attempt < maxAttempts) {
                    debug("verbose.http.retry.network", attempt, maxAttempts);
                    backoff(attempt);
                    continue;
                }
                throw ApiExceptionMapper.network();
            }
        }
        throw ApiExceptionMapper.network();
    }

    private TransportRequest toTransport(Call call) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        if (call.token() != null) {
            headers.put(ApiHeaders.ARBITER_TOKEN, call.token());
        }
        String payload = null;
        if (call.body() != null) {
            try {
                payload = mapper.writeValueAsString(call.body());
            } catch (JacksonException malformed) {
                throw ApiExceptionMapper.mapStatus(0, null, null);
            }
            headers.put("Content-Type", "application/json");
        }
        logRequest(call, payload);
        return new TransportRequest(call.method(), resolvePath(call.path()),
                headers, payload);
    }

    private DomainException mapError(TransportResponse response, List<String> secrets) {
        ApiErrorResponse apiError = null;
        if (response.body() != null && !response.body().isBlank()) {
            try {
                apiError = mapper.readValue(response.body(), ApiErrorResponse.class);
            } catch (JacksonException ignored) {
                // Map the status even when the server does not use structured errors.
            }
        }
        String code = apiError == null ? null : apiError.code();
        String message = apiError == null ? null
                : Redactor.redactSecrets(apiError.message(), secrets);
        return ApiExceptionMapper.mapStatus(response.status(), code, message);
    }

    private String resolvePath(String route) {
        String prefix = config.apiPrefix();
        String routePath = route.startsWith("/") ? route : "/" + route;
        return (prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1)
                : prefix) + routePath;
    }

    private void logRequest(Call call, String payload) {
        debug("verbose.http.request", call.method(), resolvePath(call.path()));
        if (call.token() != null) {
            debug("verbose.http.token");
        }
        if (payload != null) {
            debug("verbose.http.body", Redactor.redactBody(payload));
        }
    }

    private void debug(String key, Object... args) {
        if (verbose.getAsBoolean()) {
            logger.accept(messageFormatter.apply(key, args));
        }
    }

    private void backoff(int attempt) {
        int exponent = Math.min(Math.max(0, attempt - 1), 10);
        Duration delay = config.retryBaseDelay().multipliedBy(1L << exponent);
        if (delay.compareTo(Duration.ofSeconds(30)) > 0) {
            delay = Duration.ofSeconds(30);
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new UserAbortedException();
        }
    }

    private static boolean idempotent(String method) {
        return List.of("GET", "PUT", "DELETE").contains(method);
    }

    private static boolean canRetry(int status) {
        return status >= 500 && status <= 599;
    }

    private record Call(String method, String path, String token, Object body,
                        List<String> sensitiveValues) {
    }
}
