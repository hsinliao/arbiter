package io.github.arbiter.server.control;

/** Build version shared by the control and runtime sides of the server. */
public final class ServerVersion {
    private ServerVersion() {
    }

    public static String version() {
        String implementationVersion = ServerVersion.class.getPackage()
                .getImplementationVersion();
        return "arbiter-server "
                + (implementationVersion == null ? "development" : implementationVersion);
    }
}
