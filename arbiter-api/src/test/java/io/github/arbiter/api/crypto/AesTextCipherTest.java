package io.github.arbiter.api.crypto;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesTextCipherTest {
    private static final byte[] KEY = new byte[32];
    private static final byte[] OTHER_KEY = new byte[32];

    static {
        for (int index = 0; index < KEY.length; index++) {
            KEY[index] = (byte) index;
            OTHER_KEY[index] = (byte) (index + 1);
        }
    }

    @Test
    void roundTripsUnicodeAndEmptyText() {
        for (String plaintext : new String[]{"hello world", "中文消息", "", "line\nbreak"}) {
            String ciphertext = AesTextCipher.encrypt(plaintext, KEY);
            assertEquals(plaintext, AesTextCipher.decrypt(ciphertext, KEY));
        }
    }

    @Test
    void usesAFreshIvForEveryEncryption() {
        String first = AesTextCipher.encrypt("same", KEY);
        String second = AesTextCipher.encrypt("same", KEY);

        assertNotEquals(first, second);
        assertEquals("same", AesTextCipher.decrypt(first, KEY));
        assertEquals("same", AesTextCipher.decrypt(second, KEY));
    }

    @Test
    void wrongKeyFailsAuthentication() {
        String ciphertext = AesTextCipher.encrypt("secret", KEY);

        assertThrows(IllegalArgumentException.class,
                () -> AesTextCipher.decrypt(ciphertext, OTHER_KEY));
    }

    @Test
    void tamperedCiphertextFailsAuthentication() {
        String ciphertext = AesTextCipher.encrypt("secret", KEY);
        char[] chars = ciphertext.toCharArray();
        chars[chars.length / 2] = chars[chars.length / 2] == 'A' ? 'B' : 'A';

        assertThrows(IllegalArgumentException.class,
                () -> AesTextCipher.decrypt(new String(chars), KEY));
    }

}
