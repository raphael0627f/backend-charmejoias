package com.manager.app.shared;

import java.security.SecureRandom;

public final class NanoIdUtils {
    // so letras maiusculas (sem I/L/O/U pra evitar confusao visual com 1/0/V) - sem digito e sem
    // minuscula, pra sair sempre compativel com leitor de codigo de barras em modo Code39 padrao
    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
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
