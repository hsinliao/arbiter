package io.github.arbiter.api.config;

/**
 * Single source of truth for process-level configuration keys. Scripts and
 * documentation must use the same names; Java code should reference these
 * constants instead of repeating string literals.
 */
public final class ArbiterKeys {
    private ArbiterKeys() {
    }

    public static final class Env {
        public static final String TOKEN = "ARBITER_TOKEN";
        public static final String USERNAME = "ARBITER_USERNAME";
        public static final String LANG = "ARBITER_LANG";
        public static final String TRANSPORT = "ARBITER_TRANSPORT";

        public static final String TLS_ENABLED = "ARBITER_TLS_ENABLED";
        public static final String TLS_TRUSTSTORE = "ARBITER_TLS_TRUSTSTORE";
        public static final String TLS_TRUSTSTORE_TYPE = "ARBITER_TLS_TRUSTSTORE_TYPE";
        public static final String TLS_TRUSTSTORE_PASSWORD =
                "ARBITER_TLS_TRUSTSTORE_PASSWORD";
        public static final String TLS_KEYSTORE = "ARBITER_TLS_KEYSTORE";
        public static final String TLS_KEYSTORE_TYPE = "ARBITER_TLS_KEYSTORE_TYPE";
        public static final String TLS_KEYSTORE_PASSWORD = "ARBITER_TLS_KEYSTORE_PASSWORD";

        private Env() {
        }
    }

    public static final class SystemProperty {
        /** Distribution root, set by the launchers. */
        public static final String HOME = "arbiter.home";
        /** Overrides the user configuration directory root; tests and restricted hosts only. */
        public static final String USER_HOME = "arbiter.user.home";

        private SystemProperty() {
        }
    }
}
