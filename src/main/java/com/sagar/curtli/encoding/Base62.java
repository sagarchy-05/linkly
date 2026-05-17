package com.sagar.curtli.encoding;

import java.security.SecureRandom;

public final class Base62 {
    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int BASE = ALPHABET.length;
    private static final SecureRandom RANDOM = new SecureRandom();
    private Base62() {}

    /**
     * Generates a cryptographically random Base62 string of the given length.
     * 7 chars = 62^7 ≈ 3.5 trillion possible values — collision-resistant via
     * the birthday paradox up to several million codes before retries spike.
     */
    public static String randomCode(int length) {
        if (length <= 0) throw new IllegalArgumentException("Length must be positive");
        char[] buf = new char[length];
        for (int i = 0; i < length; i++) {
            buf[i] = ALPHABET[RANDOM.nextInt(BASE)];
        }
        return new String(buf);
    }

    public static String encode(long value) {
        if (value < 0) throw new IllegalArgumentException("Negative values not supported");
        if (value == 0) return String.valueOf(ALPHABET[0]);
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            sb.append(ALPHABET[(int) (value % BASE)]);
            value /= BASE;
        }
        return sb.reverse().toString();
    }

    public static long decode(String code) {
        long result = 0;
        for (char c : code.toCharArray()) {
            int idx = indexOf(c);
            if (idx < 0) throw new IllegalArgumentException("Invalid base62 char: " + c);
            result = result * BASE + idx;
        }
        return result;
    }

    private static int indexOf(char c) {
        for (int i = 0; i < ALPHABET.length; i++) if (ALPHABET[i] == c) return i;
        return -1;
    }
}