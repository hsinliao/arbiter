package io.github.arbiter.server.control;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Spring-free control-plane entry point: {@code --check}, {@code --stop},
 * {@code --help}, and {@code --version}. The normal boot path is
 * {@code ArbiterServerApplication}; launchers select this entry point so
 * control probes never initialize a Spring context.
 */
public final class ServerControl {
    private static final String STOPPED_MESSAGE = "STOPPED";

    public static void main(String[] args) {
        int exitCode = new ServerControl().run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    int run(String[] args) {
        Options options;
        try {
            options = parseArgs(args);
        } catch (UsageException usage) {
            System.err.println("arbiter-server: " + usage.getMessage());
            printUsage(System.err);
            return 2;
        }
        if (options.help()) {
            printUsage(System.out);
            return 0;
        }
        if (options.version()) {
            System.out.println(ServerVersion.version());
            return 0;
        }
        Path home = home();
        if (home == null) {
            return 2;
        }
        ServerPaths.Paths paths = ServerPaths.under(home);
        if (options.check()) {
            return check(paths);
        }
        if (options.stop()) {
            return ServerStop.stop(paths);
        }
        System.err.println("arbiter-server: start the server through bin/server-start");
        return 2;
    }

    private static int check(ServerPaths.Paths paths) {
        boolean running = ServerLockProbe.isRunning(paths.lockFile())
                && Files.isRegularFile(paths.readyFile());
        if (!running) {
            System.out.println(STOPPED_MESSAGE);
            return 3;
        }
        String pid;
        try {
            pid = ServerLockProbe.read(paths.lockFile());
            if (pid.isEmpty()) {
                pid = ServerLockProbe.read(paths.pidFile());
            }
        } catch (java.io.IOException failure) {
            pid = "";
        }
        System.out.println(pid.isEmpty() ? "RUNNING" : "RUNNING " + pid);
        return 0;
    }

    static Options parseArgs(String[] args) {
        boolean check = false;
        boolean stop = false;
        boolean help = false;
        boolean version = false;

        for (String argument : args) {
            if (argument == null || argument.trim().isEmpty()) {
                throw new UsageException("Empty argument");
            }
            if ("-h".equals(argument) || "--help".equals(argument)) {
                help = true;
            } else if ("-V".equals(argument) || "--version".equals(argument)) {
                version = true;
            } else if ("--check".equals(argument)) {
                check = true;
            } else if ("--stop".equals(argument)) {
                stop = true;
            } else {
                throw new UsageException(argument.startsWith("-")
                        ? "Unknown option: " + argument
                        : "Unexpected argument: " + argument);
            }
        }
        return new Options(check, stop, help, version);
    }

    /** Used by the Boot entry point to reject accidentally routed control flags. */
    public static boolean isControlCommand(String[] args) {
        for (String argument : args) {
            if ("--check".equals(argument) || "--stop".equals(argument)
                    || "--help".equals(argument) || "-h".equals(argument)
                    || "--version".equals(argument) || "-V".equals(argument)) {
                return true;
            }
        }
        return false;
    }

    private static Path home() {
        String configured = System.getProperty("arbiter.home");
        if (configured == null || configured.trim().isEmpty()) {
            System.err.println("arbiter-server: arbiter.home is not set;"
                    + " start the server through bin/server-start");
            return null;
        }
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    private static void printUsage(PrintStream out) {
        out.println("Usage: arbiter-server [OPTIONS]");
        out.println();
        out.println("Options:");
        out.println("  --check                       Print RUNNING <pid> or STOPPED.");
        out.println("  --stop                        Ask the running server to stop.");
        out.println("  -h, --help                    Show this help and exit.");
        out.println("  -V, --version                 Show version and exit.");
    }

    static final class Options {
        private final boolean check;
        private final boolean stop;
        private final boolean help;
        private final boolean version;

        Options(boolean check, boolean stop, boolean help, boolean version) {
            this.check = check;
            this.stop = stop;
            this.help = help;
            this.version = version;
        }

        boolean check() {
            return check;
        }

        boolean stop() {
            return stop;
        }

        boolean help() {
            return help;
        }

        boolean version() {
            return version;
        }
    }

    static final class UsageException extends RuntimeException {
        UsageException(String message) {
            super(message);
        }
    }
}
