package io.github.arbiter.server.config;

import io.github.arbiter.api.crypto.CryptoKeys;
import io.github.arbiter.server.auth.FileUserRepository;
import io.github.arbiter.server.auth.LoginRateLimiter;
import io.github.arbiter.server.auth.TokenService;
import io.github.arbiter.server.runtime.ServerRuntime;
import io.github.arbiter.server.tls.SslBundleFactory;
import io.github.arbiter.server.uds.UdsRequestDispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.Shutdown;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ServerProperties.class)
public final class ServerConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(ServerConfiguration.class);
    private static final String INITIAL_ADMIN_FILE = "initial-admin.txt";

    @Bean
    ServerConfig serverConfig(ServerProperties properties) {
        return properties.toServerConfig();
    }

    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webServerCustomizer(
            ServerConfig config) {
        return factory -> {
            try {
                factory.setAddress(InetAddress.getByName(config.host()));
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "Invalid server host: " + config.host(), failure);
            }
            factory.setPort(config.port());
            factory.setShutdown(Shutdown.GRACEFUL);
            if (config.tls().enabled()) {
                try {
                    SslBundles bundles = SslBundleFactory
                            .create(config.tls());
                    factory.setSslBundles(bundles);
                    Ssl ssl = Ssl.forBundle(SslBundleFactory.BUNDLE_NAME);
                    if (config.tls().clientAuth()) {
                        ssl.setClientAuth(Ssl.ClientAuth.NEED);
                    }
                    factory.setSsl(ssl);
                } catch (Exception failure) {
                    throw new IllegalStateException(
                            "Unable to initialize TLS material", failure);
                }
            }
        };
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TokenService tokenService(ServerConfig config, Clock clock) throws IOException {
        if (CryptoKeys.ensure()) {
            LOG.info("generated crypto key at {}", CryptoKeys.path());
        }
        return new TokenService(CryptoKeys.ring(), config.tokenTtl(), clock);
    }

    @Bean
    LoginRateLimiter loginRateLimiter(ServerConfig config, Clock clock) {
        return new LoginRateLimiter(config.loginMaxFailures(),
                config.loginWindow(), clock);
    }

    @Bean
    FileUserRepository userRepository() throws IOException {
        FileUserRepository.OpenResult result = FileUserRepository.openOrCreate();
        FileUserRepository repository = result.repository();
        if (result.initialAdmin().isPresent()) {
            FileUserRepository.InitialAdmin initial = result.initialAdmin().orElseThrow();
            Path credentials = writeInitialAdmin(repository.path(), initial);
            LOG.warn("initial administrator created: username={} credentials={}"
                            + " (read it once, then delete the file)",
                    initial.username(), credentials);
        } else {
            LOG.info("loaded user store file={} users={}",
                    repository.path(), repository.findAll().size());
        }
        return repository;
    }

    private static Path writeInitialAdmin(Path usersFile, FileUserRepository.InitialAdmin initial)
            throws IOException {
        Path credentials = usersFile.resolveSibling(INITIAL_ADMIN_FILE);
        Files.writeString(credentials,
                "username=" + initial.username() + System.lineSeparator()
                        + "password=" + initial.password() + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        try {
            Files.setPosixFilePermissions(credentials,
                    PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and non-POSIX filesystems rely on the profile ACL.
        }
        return credentials;
    }

    @Bean(destroyMethod = "close")
    ServerRuntime serverRuntime(ServerConfig config,
                                UdsRequestDispatcher dispatcher) throws Exception {
        return new ServerRuntime(config, dispatcher);
    }
}
