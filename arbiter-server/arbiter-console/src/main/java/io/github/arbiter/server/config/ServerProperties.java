package io.github.arbiter.server.config;

import io.github.arbiter.api.config.ArbiterHome;
import io.github.arbiter.server.tls.TlsSettings;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Boot-native binding for the {@code arbiter.*} server keys. The property
 * source is loaded from {@code conf/common.properties} and
 * {@code conf/server.properties}; the values are mapped to the immutable
 * {@link ServerConfig} used by the runtime.
 */
@ConfigurationProperties(prefix = "arbiter")
public final class ServerProperties {
    private final Api api = new Api();
    private String transport = "http";
    private final Server server = new Server();

    public Api getApi() {
        return api;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public Server getServer() {
        return server;
    }

    public ServerConfig toServerConfig() {
        ServerTransport.Mode mode = ServerTransport.parseMode(transport);
        Path socketPath = mode == ServerTransport.Mode.HTTP
                ? null : ArbiterHome.defaultSocketPath();
        TlsSettings tls = TlsSettings.from(
                server.tls.enabled,
                server.tls.keystore.type,
                server.tls.keystore.password,
                server.tls.client.auth,
                server.tls.truststore.type,
                server.tls.truststore.password,
                server.tls.protocols);
        return new ServerConfig(
                server.host,
                server.port,
                api.prefix,
                Duration.ofSeconds(server.token.ttl.seconds),
                server.max.request.bytes,
                server.login.max.failures,
                Duration.ofSeconds(server.login.window.seconds),
                tls,
                new ServerTransport(mode, socketPath));
    }

    public static final class Api {
        private String prefix = "/api/v1";

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }
    }

    public static final class Server {
        private String host = "127.0.0.1";
        private int port = 8080;
        private final Token token = new Token();
        private final Max max = new Max();
        private final Login login = new Login();
        private final Tls tls = new Tls();

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public Token getToken() {
            return token;
        }

        public Max getMax() {
            return max;
        }

        public Login getLogin() {
            return login;
        }

        public Tls getTls() {
            return tls;
        }
    }

    public static final class Token {
        private final Ttl ttl = new Ttl();

        public Ttl getTtl() {
            return ttl;
        }
    }

    public static final class Ttl {
        private long seconds = 3600;

        public long getSeconds() {
            return seconds;
        }

        public void setSeconds(long seconds) {
            this.seconds = seconds;
        }
    }

    public static final class Max {
        private final Request request = new Request();

        public Request getRequest() {
            return request;
        }
    }

    public static final class Request {
        private int bytes = 65_536;

        public int getBytes() {
            return bytes;
        }

        public void setBytes(int bytes) {
            this.bytes = bytes;
        }
    }

    public static final class Login {
        private final Failures max = new Failures();
        private final Window window = new Window();

        public Failures getMax() {
            return max;
        }

        public Window getWindow() {
            return window;
        }
    }

    public static final class Failures {
        private int failures = 5;

        public int getFailures() {
            return failures;
        }

        public void setFailures(int failures) {
            this.failures = failures;
        }
    }

    public static final class Window {
        private long seconds = 60;

        public long getSeconds() {
            return seconds;
        }

        public void setSeconds(long seconds) {
            this.seconds = seconds;
        }
    }

    public static final class Tls {
        private boolean enabled;
        private final Keystore keystore = new Keystore();
        private final Client client = new Client();
        private final Truststore truststore = new Truststore();
        private String protocols = "TLSv1.3,TLSv1.2";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Keystore getKeystore() {
            return keystore;
        }

        public Client getClient() {
            return client;
        }

        public Truststore getTruststore() {
            return truststore;
        }

        public String getProtocols() {
            return protocols;
        }

        public void setProtocols(String protocols) {
            this.protocols = protocols;
        }
    }

    public static final class Keystore {
        private String type = "PKCS12";
        private String password;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static final class Client {
        private boolean auth;

        public boolean isAuth() {
            return auth;
        }

        public void setAuth(boolean auth) {
            this.auth = auth;
        }
    }

    public static final class Truststore {
        private String type = "PKCS12";
        private String password;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
