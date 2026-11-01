package io.github.arbiter.cli;

import java.io.Console;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class OutputEncoding {
    private OutputEncoding() {
    }

    static PrintWriter writer(OutputStream stream) {
        return new PrintWriter(stream, true, charset(stream));
    }

    static Charset charset(OutputStream stream) {
        if (stream != System.out && stream != System.err) {
            return StandardCharsets.UTF_8;
        }
        Console console = System.console();
        if (console != null) {
            return console.charset();
        }
        String property = stream == System.err ? "stderr.encoding" : "stdout.encoding";
        String encoding = System.getProperty(property);
        if (encoding != null && !encoding.isBlank()) {
            try {
                return Charset.forName(encoding);
            } catch (IllegalArgumentException ignored) {
                // Fall through to the portable UTF-8 default.
            }
        }
        return StandardCharsets.UTF_8;
    }
}
