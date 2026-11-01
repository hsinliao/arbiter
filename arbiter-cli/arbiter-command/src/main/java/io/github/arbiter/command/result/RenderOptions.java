package io.github.arbiter.command.result;

public record RenderOptions(String output, String language, boolean verbose) {
    public boolean json() {
        return "json".equalsIgnoreCase(output);
    }
}
