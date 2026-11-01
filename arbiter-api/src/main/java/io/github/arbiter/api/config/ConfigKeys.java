package io.github.arbiter.api.config;

/**
 * Property keys read from {@code conf/common.properties},
 * {@code conf/cli.properties}, and {@code conf/server.properties}.
 *
 * <p>Every key starts with {@code arbiter.} and is grouped by the process or
 * concern that owns it. {@link Common} keys are shared by both processes;
 * {@link Client} keys belong to the CLI; {@link Server} keys belong to the
 * server.</p>
 */
public final class ConfigKeys {
    private ConfigKeys() {
    }

    /** Shared settings loaded from {@code conf/common.properties}. */
    public static final class Common {
        public static final String SERVER_HOST = "arbiter.server.host";
        public static final String SERVER_PORT = "arbiter.server.port";
        public static final String TRANSPORT = "arbiter.transport";
        public static final String API_PREFIX = "arbiter.api.prefix";

        private Common() {
        }
    }

    /** Client settings loaded from {@code conf/cli.properties}. */
    public static final class Client {
        public static final String HOST = "arbiter.client.host";
        public static final String CONNECT_TIMEOUT_MS =
                "arbiter.client.connect.timeout.ms";
        public static final String READ_TIMEOUT_MS = "arbiter.client.read.timeout.ms";
        public static final String POOL_ACQUIRE_TIMEOUT_MS =
                "arbiter.client.pool.acquire.timeout.ms";
        public static final String POOL_PERMITS = "arbiter.client.pool.permits";
        public static final String RETRY_MAX = "arbiter.client.retry.max";
        public static final String RETRY_BASE_DELAY_MS =
                "arbiter.client.retry.base.delay.ms";

        public static final class Tls {
            public static final String ENABLED = "arbiter.client.tls.enabled";
            public static final String TRUSTSTORE = "arbiter.client.tls.truststore";
            public static final String TRUSTSTORE_TYPE =
                    "arbiter.client.tls.truststore.type";
            public static final String KEYSTORE = "arbiter.client.tls.keystore";
            public static final String KEYSTORE_TYPE = "arbiter.client.tls.keystore.type";

            private Tls() {
            }
        }

        private Client() {
        }
    }

    /** Server settings loaded from {@code conf/server.properties}. */
    public static final class Server {
        public static final String TOKEN_TTL_SECONDS =
                "arbiter.server.token.ttl.seconds";
        public static final String MAX_REQUEST_BYTES =
                "arbiter.server.max.request.bytes";
        public static final String LOGIN_MAX_FAILURES =
                "arbiter.server.login.max.failures";
        public static final String LOGIN_WINDOW_SECONDS =
                "arbiter.server.login.window.seconds";
        public static final class Tls {
            public static final String ENABLED = "arbiter.server.tls.enabled";
            public static final String KEYSTORE_TYPE =
                    "arbiter.server.tls.keystore.type";
            public static final String KEYSTORE_PASSWORD =
                    "arbiter.server.tls.keystore.password";
            public static final String CLIENT_AUTH =
                    "arbiter.server.tls.client.auth";
            public static final String TRUSTSTORE_TYPE =
                    "arbiter.server.tls.truststore.type";
            public static final String TRUSTSTORE_PASSWORD =
                    "arbiter.server.tls.truststore.password";
            public static final String PROTOCOLS = "arbiter.server.tls.protocols";

            private Tls() {
            }
        }

        private Server() {
        }
    }
}
