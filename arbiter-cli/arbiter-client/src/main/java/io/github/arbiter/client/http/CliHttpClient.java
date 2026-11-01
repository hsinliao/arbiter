package io.github.arbiter.client.http;

import io.github.arbiter.client.ApiClient;
import io.github.arbiter.client.config.HttpClientConfig;
import io.github.arbiter.client.transport.HttpApiTransport;

import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Backward-compatible HTTP client facade. New code should use
 * {@link ApiClient} with an explicit {@code ApiTransport}.
 */
@Deprecated
public final class CliHttpClient extends ApiClient {
    public CliHttpClient(HttpClientConfig config, BooleanSupplier verbose,
                         BiFunction<String, Object[], String> messageFormatter,
                         Consumer<String> logger) {
        super(config.apiClientConfig(), new HttpApiTransport(config.httpTransportConfig()),
                verbose, messageFormatter, logger);
    }
}
