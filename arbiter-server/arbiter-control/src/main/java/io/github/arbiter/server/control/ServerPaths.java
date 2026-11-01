package io.github.arbiter.server.control;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Fixed paths and configuration loading rooted at {@code arbiter.home}. */
public final class ServerPaths {
    private ServerPaths() {
    }

    public static Paths under(Path home) {
        Path conf = home.resolve("conf");
        Path run = home.resolve("run");
        return new Paths(conf.resolve("server.properties"),
                conf.resolve("common.properties"), run,
                run.resolve(".server.pid"), run.resolve(".server.stop"),
                run.resolve(".server.lock"), run.resolve(".server.ready"));
    }

    public static Properties loadProperties(Paths paths) throws IOException {
        Properties properties = new Properties();
        loadIfPresent(properties, paths.commonConfig());
        loadIfPresent(properties, paths.config());
        return properties;
    }

    private static void loadIfPresent(Properties properties, Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return;
        }
        Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        try {
            properties.load(reader);
        } finally {
            reader.close();
        }
    }

    public static final class Paths {
        private final Path config;
        private final Path commonConfig;
        private final Path runDirectory;
        private final Path pidFile;
        private final Path stopFile;
        private final Path lockFile;
        private final Path readyFile;

        public Paths(Path config, Path commonConfig, Path runDirectory,
                     Path pidFile, Path stopFile, Path lockFile, Path readyFile) {
            this.config = config;
            this.commonConfig = commonConfig;
            this.runDirectory = runDirectory;
            this.pidFile = pidFile;
            this.stopFile = stopFile;
            this.lockFile = lockFile;
            this.readyFile = readyFile;
        }

        public Path config() {
            return config;
        }

        public Path commonConfig() {
            return commonConfig;
        }

        public Path runDirectory() {
            return runDirectory;
        }

        public Path pidFile() {
            return pidFile;
        }

        public Path stopFile() {
            return stopFile;
        }

        public Path lockFile() {
            return lockFile;
        }

        public Path readyFile() {
            return readyFile;
        }
    }
}
