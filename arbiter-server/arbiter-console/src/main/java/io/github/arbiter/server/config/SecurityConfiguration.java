package io.github.arbiter.server.config;

import io.github.arbiter.api.crypto.CryptoKeys;
import io.github.arbiter.server.auth.FileUserRepository;
import io.github.arbiter.server.auth.LoginRateLimiter;
import io.github.arbiter.server.auth.TokenService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;

/** Token, user store, and login-rate-limit runtime beans. */
@Configuration(proxyBeanMethods = false)
public final class SecurityConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfiguration.class);
    private static final String INITIAL_ADMIN_FILE = "initial-admin.txt";

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
}
