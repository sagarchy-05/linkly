package com.sagar.linkly.encoding;

public final class Base62 {
    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int BASE = ALPHABET.length;
    private Base62() {}

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