package io.github.arbiter.command.result;

import java.util.Map;

/**
 * Typed command payload. Commands expose a record instead of assembling an
 * ad-hoc map; JSON serialization still consumes the stable map projection.
 */
public interface ResultPayload {
    Map<String, Object> toData();
}
