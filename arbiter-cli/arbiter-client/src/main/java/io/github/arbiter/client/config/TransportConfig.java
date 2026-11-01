package io.github.arbiter.client.config;

/**
 * Transport selection for the API client. HTTP carries TLS settings; Unix
 * domain sockets carry a filesystem path instead.
 */
public sealed interface TransportConfig
        permits HttpTransportConfig, UnixSocketTransportConfig {
}
