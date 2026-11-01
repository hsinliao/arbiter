package io.github.arbiter.client.transport;

import io.github.arbiter.client.config.HttpTransportConfig;
import io.github.arbiter.client.tls.ClientSslContextFactory;
import io.github.arbiter.core.exception.NetworkException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * HTTP transport adapter. It intentionally does not know about DTOs, retries,
 * token headers, or DomainException mapping.
 */
public final class HttpApiTransport implements ApiTransport {
    private final HttpTransportConfig config;
    private final HttpClient client;
    private final Semaphore poolPermits;

    public HttpApiTransport(HttpTransportConfig config) {
        this.config = config;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER);
        if (config.tls().enabled()) {
            try {
                builder.sslContext(ClientSslContextFactory.create(config.tls()));
            } catch (GeneralSecurityException | IOException failure) {
                throw new IllegalArgumentException("Unable to initialize TLS", failure);
            }
        }
        this.client = builder.build();
        this.poolPermits = new Semaphore(config.poolPermits(), true);
    }

    @Override
    public TransportResponse execute(TransportRequest request)
            throws IOException, InterruptedException {
        boolean acquired = false;
        try {
            acquired = poolPermits.tryAcquire(config.poolAcquireTimeout().toMillis(),
                    TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new NetworkException();
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(request.path()))
                    .timeout(config.readTimeout());
            request.headers().forEach(builder::header);
            if (request.body() == null) {
                builder.method(request.method(), HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(request.method(),
                        HttpRequest.BodyPublishers.ofString(request.body()));
            }
            try {
                HttpResponse<String> response = client.send(builder.build(),
                        HttpResponse.BodyHandlers.ofString());
                Map<String, String> headers = new LinkedHashMap<>();
                response.headers().map().forEach((name, values) -> {
                    if (values != null && !values.isEmpty()) {
                        headers.put(name, values.get(0));
                    }
                });
                return new TransportResponse(response.statusCode(), headers,
                        response.body());
            } catch (HttpTimeoutException | ConnectException exception) {
                throw new NetworkException(exception);
            } catch (IOException exception) {
                throw new NetworkException(exception);
            }
        } finally {
            if (acquired) {
                poolPermits.release();
            }
        }
    }

    private URI resolve(String path) {
        int queryIndex = path.indexOf('?');
        String routePath = queryIndex < 0 ? path : path.substring(0, queryIndex);
        String query = queryIndex < 0 ? null : path.substring(queryIndex + 1);
        String basePath = config.baseUri().getPath();
        String joined = (basePath == null ? "" : basePath).replaceAll("/+$", "")
                + (routePath.startsWith("/") ? routePath : "/" + routePath);
        try {
            return new URI(config.baseUri().getScheme(), config.baseUri().getUserInfo(),
                    config.baseUri().getHost(), config.baseUri().getPort(),
                    joined, query, null);
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid API route", exception);
        }
    }
}
