package io.github.arbiter.agent.llm;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class StructuredOutput {

    private static final ObjectMapper JSON = new ObjectMapper();

    private StructuredOutput() {
    }

    /**
     * Extracts the first balanced JSON object or array from a model response.
     */
    public static String extractJson(String content) {
        if (content == null || content.isBlank()) {
            throw new StructuredOutputException("Model returned no content");
        }
        for (int start = 0; start < content.length(); start++) {
            char opening = content.charAt(start);
            if (opening != '{' && opening != '[') {
                continue;
            }
            String candidate = balancedValue(content, start, opening);
            if (candidate == null) {
                continue;
            }
            try {
                JSON.readValue(candidate, Object.class);
                return candidate;
            } catch (JacksonException ignored) {
                // Natural-language brackets can precede the actual JSON value.
            }
        }
        throw new StructuredOutputException("Model response does not contain valid JSON");
    }

    private static String balancedValue(String content, int start, char opening) {
        char closing = opening == '{' ? '}' : ']';
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < content.length(); index++) {
            char current = content.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == opening) {
                depth++;
            } else if (current == closing) {
                depth--;
                if (depth == 0) {
                    return content.substring(start, index + 1);
                }
            }
        }
        return null;
    }

    public static final class StructuredOutputException extends RuntimeException {
        public StructuredOutputException(String message) {
            super(message);
        }
    }
}
