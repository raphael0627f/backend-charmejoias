package com.manager.app.shared;

import java.security.SecureRandom;

public final class NanoIdUtils {
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZabcdefghjkmnpqrstvwxyz".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private NanoIdUtils() {}

    public static String generate(int tamanho) {
        StringBuilder sb = new StringBuilder(tamanho);
        for (int i = 0; i < tamanho; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
