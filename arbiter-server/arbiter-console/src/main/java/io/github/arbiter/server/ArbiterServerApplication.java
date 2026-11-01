package io.github.arbiter.server;

import io.github.arbiter.server.launcher.ServerLauncher;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot composition root and executable entry point. */
@SpringBootApplication(proxyBeanMethods = false)
public class ArbiterServerApplication {
    public ArbiterServerApplication() {
    }

    public static void main(String[] args) {
        new ServerLauncher().start(args);
    }
}
