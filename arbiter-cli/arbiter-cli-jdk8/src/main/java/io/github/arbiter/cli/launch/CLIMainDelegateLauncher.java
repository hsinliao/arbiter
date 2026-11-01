package io.github.arbiter.cli.launch;

import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class CLIMainDelegateLauncher {

    private static final int MIN_REQUIRED_MAJOR = 17;

    public static void main(String[] args) {
        // ---------- 1. Pre-check the Java version (pure string parsing only) ----------
        VersionCheckResult check = JavaVersionChecker.check(MIN_REQUIRED_MAJOR);
        if (!check.isOk()) {
            System.err.println("[launcher] " + check.getMessage());
            System.exit(2);
            return;
        }
        try {
            Class<?> cliMainClass = Class.forName("io.github.arbiter.cli.CLIMain");
            Map<String,String> env = System.getenv();
            Method runMethod = cliMainClass.getDeclaredMethod(
                    "run",
                    String[].class,
                    Map.class,
                    Path.class,
                    OutputStream.class,
                    OutputStream.class
            );
            Path workingDir = Paths.get("").toAbsolutePath();
            int exitCode = (int) runMethod.invoke(
                    null,
                    args,
                    env,
                    workingDir,
                    System.out,
                    System.err
            );
            System.exit(exitCode);
        } catch (ClassNotFoundException | NoSuchMethodException
                 | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
