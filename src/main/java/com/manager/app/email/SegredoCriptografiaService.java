package com.manager.app.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

// criptografia em repouso (AES-GCM, IV aleatorio por valor) de segredos guardados no banco, como a senha do SMTP.
// A chave e derivada (SHA-256) de APP_SEGREDO_CRIPTOGRAFIA; se ela nao existir, do segredo do JWT (JWT_SECRET).
// RISCO: trocar essa chave (ou o JWT_SECRET, quando for o caso) torna ilegivel a senha ja salva - basta informa-la
// de novo na tela de configuracao de e-mail. Quem acessar o banco E o segredo do servidor consegue ler a senha.
@Service
public class SegredoCriptografiaService {

    private static final String PREFIXO = "v1:";
    private static final int TAMANHO_IV = 12;
    private static final int TAMANHO_TAG_BITS = 128;

    private final byte[] chave;
    private final SecureRandom random = new SecureRandom();

    public SegredoCriptografiaService(@Value("${app.seguranca.segredo-criptografia:}") String segredoDedicado,
                                      @Value("${jwt.secret:}") String segredoJwt) {
        String segredo = segredoDedicado != null && !segredoDedicado.isBlank() ? segredoDedicado : segredoJwt;
        if (segredo == null || segredo.isBlank()) {
            throw new IllegalStateException("Segredo de criptografia não configurado - defina APP_SEGREDO_CRIPTOGRAFIA (ou JWT_SECRET)");
        }
        try {
            this.chave = MessageDigest.getInstance("SHA-256").digest(segredo.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Algoritmo de derivação da chave indisponível", e);
        }
    }

    public String criptografar(String textoPuro){
        try {
            byte[] iv = new byte[TAMANHO_IV];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(chave, "AES"), new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(textoPuro.getBytes(StandardCharsets.UTF_8));
            byte[] saida = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, saida, 0, iv.length);
            System.arraycopy(cifrado, 0, saida, iv.length, cifrado.length);
            return PREFIXO + Base64.getEncoder().encodeToString(saida);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Não foi possível criptografar o valor", e);
        }
    }

    // lanca SegredoIlegivelException se o valor nao foi gravado com a chave atual (ou esta corrompido)
    public String descriptografar(String valor){
        try {
            if (valor == null || !valor.startsWith(PREFIXO)) {
                throw new SegredoIlegivelException();
            }
            byte[] entrada = Base64.getDecoder().decode(valor.substring(PREFIXO.length()));
            if (entrada.length <= TAMANHO_IV) {
                throw new SegredoIlegivelException();
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(chave, "AES"), new GCMParameterSpec(TAMANHO_TAG_BITS, entrada, 0, TAMANHO_IV));
            return new String(cipher.doFinal(entrada, TAMANHO_IV, entrada.length - TAMANHO_IV), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new SegredoIlegivelException();
        }
    }

    public static class SegredoIlegivelException extends RuntimeException {
        public SegredoIlegivelException() {
            super("Não foi possível ler a senha salva. Informe a senha novamente na configuração de e-mail.");
        }
    }
}
