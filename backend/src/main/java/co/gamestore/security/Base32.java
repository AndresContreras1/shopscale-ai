package co.gamestore.security;

import java.util.Locale;

/**
 * Base32 as RFC 4648 defines it, which is the alphabet authenticator apps expect for a shared secret.
 * Base64 is not interchangeable here: the app would read a different secret and every code would fail.
 */
final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32() {
    }

    static String encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = buffer << 8 | b & 0xFF;
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                out.append(ALPHABET.charAt(buffer >> bitsLeft & 0x1F));
            }
        }
        if (bitsLeft > 0) {
            out.append(ALPHABET.charAt(buffer << 5 - bitsLeft & 0x1F));
        }
        return out.toString();
    }

    static byte[] decode(String encoded) {
        String clean = encoded.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        byte[] out = new byte[clean.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (char c : clean.toCharArray()) {
            int value = ALPHABET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Not a base32 character: " + c);
            }
            buffer = buffer << 5 | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out[index++] = (byte) (buffer >> bitsLeft & 0xFF);
            }
        }
        return out;
    }
}
