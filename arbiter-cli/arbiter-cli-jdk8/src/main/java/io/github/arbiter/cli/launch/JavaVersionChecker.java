package io.github.arbiter.cli.launch;

public class JavaVersionChecker {

    private JavaVersionChecker() {
    }

    public static VersionCheckResult check(int requiredMajor) {
        return check(requiredMajor,
                System.getProperty("java.specification.version"),
                System.getProperty("java.version"));
    }

    /**
     * Package-private for unit tests to inject spec/full versions.
     * Consults both specification.version and java.version and takes the
     * conservative (smaller) value when they disagree.
     */
    static VersionCheckResult check(int requiredMajor, String specVer, String fullVer) {
        if (requiredMajor <= 0) {
            return VersionCheckResult.fail(requiredMajor, -1, specVer,
                    "Invalid requiredMajor: " + requiredMajor);
        }

        int fromSpec = tryParseMajor(specVer);
        int fromFull = tryParseMajor(fullVer);

        int actual;
        if (fromSpec > 0 && fromFull > 0 && fromSpec != fromFull) {
            // If the two sources disagree, take the smaller value so that we
            // never silently accept a runtime that may not satisfy the
            // requirement.
            actual = Math.min(fromSpec, fromFull);
        } else {
            actual = fromSpec > 0 ? fromSpec : fromFull;
        }

        if (actual < 0) {
            return VersionCheckResult.fail(requiredMajor, -1, specVer,
                    "Cannot determine Java version (spec=" + specVer
                            + ", java.version=" + fullVer + ")");
        }

        if (actual < requiredMajor) {
            return VersionCheckResult.fail(requiredMajor, actual, specVer,
                    "Java " + requiredMajor + "+ is required, but Java " + actual
                            + " was found (java.specification.version=" + specVer + ")");
        }

        return VersionCheckResult.ok(requiredMajor, actual, specVer);
    }

    /**
     * Parses the major version number. Supported inputs:
     *   1.8 / 1.8.0_202 -> 8
     *   9               -> 9
     *   17 / 17.0.15    -> 17
     *   21-ea / 21+35   -> 21  (lenient about vendor/preview suffixes)
     *
     * @return the major version, or -1 if the input cannot be parsed.
     */
    static int tryParseMajor(String ver) {
        if (ver == null) {
            return -1;
        }
        String s = ver.trim();
        if (s.isEmpty()) {
            return -1;
        }

        int i = 0;
        if (s.startsWith("1.")) {
            i = 2;
        }
        int start = i;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            i++;
        }
        if (i == start) {
            return -1;
        }

        try {
            return Integer.parseInt(s.substring(start, i));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

