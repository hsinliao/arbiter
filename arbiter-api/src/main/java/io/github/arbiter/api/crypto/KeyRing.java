package io.github.arbiter.api.crypto;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A set of named keys plus the key used for new encryptions. Old ciphertexts
 * keep decrypting as long as their key id remains in the ring.
 */
public record KeyRing(String activeId, Map<String, byte[]> keys) {
    public KeyRing {
        Map<String, byte[]> copy = new LinkedHashMap<>();
        keys.forEach((id, key) -> copy.put(id, key.clone()));
        keys = Map.copyOf(copy);
        if (activeId == null || !keys.containsKey(activeId)) {
            throw new IllegalArgumentException("active key is not in the ring: " + activeId);
        }
    }

    public KeyMaterial active() {
        return new KeyMaterial(activeId, keys.get(activeId));
    }

    public Optional<KeyMaterial> find(String id) {
        byte[] key = id == null ? null : keys.get(id);
        return key == null ? Optional.empty() : Optional.of(new KeyMaterial(id, key));
    }

    public Set<String> keyIds() {
        return keys.keySet();
    }
}
