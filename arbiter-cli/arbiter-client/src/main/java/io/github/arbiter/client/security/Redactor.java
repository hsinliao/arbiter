package io.github.arbiter.client.security;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import io.github.arbiter.client.http.JacksonSupport;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

public final class Redactor {
    public static final String MASK = "***";
    private static final Pattern JSON_SECRET = Pattern.compile(
            "(\"(?:password|token)\"\\s*:\\s*\")(?:\\\\.|[^\"\\\\])*(\")",
            Pattern.CASE_INSENSITIVE);
    private static final ObjectMapper MAPPER = JacksonSupport.create();

    private Redactor() {
    }

    public static String redactBody(String body) {
        if (body == null) {
            return "";
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            maskJson(root);
            return MAPPER.writeValueAsString(root);
        } catch (JacksonException malformed) {
            return JSON_SECRET.matcher(body).replaceAll("$1" + MASK + "$2");
        }
    }

    public static String redactSecrets(String text, Collection<String> secrets) {
        if (text == null) {
            return "";
        }
        String result = text;
        for (String secret : secrets) {
            if (secret != null && !secret.isEmpty()) {
                result = result.replace(secret, MASK);
            }
        }
        return redactBody(result);
    }

    private static void maskJson(JsonNode node) {
        if (node instanceof ObjectNode object) {
            for (Map.Entry<String, JsonNode> field : object.properties()) {
                if (field.getKey().equalsIgnoreCase("password")
                        || field.getKey().equalsIgnoreCase("token")) {
                    object.put(field.getKey(), MASK);
                } else {
                    maskJson(field.getValue());
                }
            }
        } else if (node != null && node.isArray()) {
            node.values().forEach(Redactor::maskJson);
        }
    }

}
