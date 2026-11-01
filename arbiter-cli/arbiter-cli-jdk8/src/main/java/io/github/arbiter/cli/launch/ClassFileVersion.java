package io.github.arbiter.cli.launch;

/**
 * Maps class file major versions to Java major versions, used for richer
 * diagnostics when an UnsupportedClassVersionError is reported. Only common
 * versions are covered; unknown versions return -1 and do not affect the
 * main control flow.
 */
public class ClassFileVersion {

    private ClassFileVersion() {
    }

    static int toJavaMajor(int classFileMajor) {
        if (classFileMajor == 52) return 8;
        if (classFileMajor == 53) return 9;
        if (classFileMajor == 54) return 10;
        if (classFileMajor == 55) return 11;
        if (classFileMajor == 56) return 12;
        if (classFileMajor == 57) return 13;
        if (classFileMajor == 58) return 14;
        if (classFileMajor == 59) return 15;
        if (classFileMajor == 60) return 16;
        if (classFileMajor == 61) return 17;
        if (classFileMajor == 62) return 18;
        if (classFileMajor == 63) return 19;
        if (classFileMajor == 64) return 20;
        if (classFileMajor == 65) return 21;
        return -1;
    }
}
