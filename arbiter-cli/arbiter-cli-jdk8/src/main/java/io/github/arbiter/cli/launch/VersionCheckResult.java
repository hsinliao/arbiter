package io.github.arbiter.cli.launch;

public final class VersionCheckResult {

    private final boolean ok;
    private final int requiredMajor;
    private final int actualMajor;   // -1 means unknown
    private final String rawVersion;
    private final String message;

    private VersionCheckResult(boolean ok, int requiredMajor, int actualMajor,
                               String rawVersion, String message) {
        this.ok = ok;
        this.requiredMajor = requiredMajor;
        this.actualMajor = actualMajor;
        this.rawVersion = rawVersion;
        this.message = message;
    }

    static VersionCheckResult ok(int requiredMajor, int actualMajor, String rawVersion) {
        return new VersionCheckResult(true, requiredMajor, actualMajor, rawVersion, null);
    }

    static VersionCheckResult fail(int requiredMajor, int actualMajor,
                                   String rawVersion, String message) {
        return new VersionCheckResult(false, requiredMajor, actualMajor, rawVersion, message);
    }

    public boolean isOk() { return ok; }
    public int getRequiredMajor() { return requiredMajor; }
    public int getActualMajor() { return actualMajor; }
    public String getRawVersion() { return rawVersion; }
    public String getMessage() { return message; }

    @Override
    public String toString() {
        return "VersionCheckResult{ok=" + ok + ", required=" + requiredMajor
                + ", actual=" + actualMajor + ", raw='" + rawVersion + "'"
                + (message == null ? "" : ", message='" + message + "'") + "}";
    }
}
