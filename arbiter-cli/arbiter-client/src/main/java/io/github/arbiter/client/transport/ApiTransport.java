package io.github.arbiter.client.transport;

import java.io.IOException;

/**
 * Low-level transport. Implementations send one request and return the raw
 * response; JSON, retries, token headers, and error mapping live above this.
 */
public interface ApiTransport extends AutoCloseable {
    TransportResponse execute(TransportRequest request)
            throws IOException, InterruptedException;

    @Override
    default void close() {
    }
}
