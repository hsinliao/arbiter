package io.github.arbiter.api.crypto;

/** One key in the ring, identified by the id stored in the envelope. */
public record KeyMaterial(String id, byte[] key) {
    public KeyMaterial {
        key = key.clone();
    }

    @Override
    public byte[] key() {
        return key.clone();
    }
}
