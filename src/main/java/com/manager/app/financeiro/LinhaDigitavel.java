package com.manager.app.financeiro;

import com.manager.app.shared.exception.ItemInvalidoException;

// validação opcional da linha digitável: 47 dígitos (boleto bancário) ou 48 dígitos (arrecadação/convênio)
public final class LinhaDigitavel {

    private LinhaDigitavel() {}

    // devolve só os dígitos (null se em branco); aceita somente dígitos, pontos e espaços
    public static String normalizar(String texto) {
        if (texto == null || texto.isBlank()) return null;
        StringBuilder digitos = new StringBuilder();
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c >= '0' && c <= '9') {
                digitos.append(c);
            } else if (c != '.' && c != ' ') {
                throw new ItemInvalidoException("A linha digitável deve conter apenas números, pontos e espaços.");
            }
        }
        String limpo = digitos.toString();
        if (limpo.length() != 47 && limpo.length() != 48) {
            throw new ItemInvalidoException("A linha digitável deve ter 47 dígitos (boleto bancário) ou 48 dígitos (arrecadação).");
        }
        boolean valida = limpo.length() == 47 ? validarBoleto(limpo) : validarArrecadacao(limpo);
        if (!valida) {
            throw new ItemInvalidoException("A linha digitável informada é inválida: o dígito verificador não confere.");
        }
        return limpo;
    }

    // boleto bancário: DV dos 3 primeiros campos (módulo 10) e DV geral (módulo 11) sobre o código de barras de 43 dígitos
    static boolean validarBoleto(String l) {
        if (dvModulo10(l.substring(0, 9)) != digito(l, 9)) return false;
        if (dvModulo10(l.substring(10, 20)) != digito(l, 20)) return false;
        if (dvModulo10(l.substring(21, 31)) != digito(l, 31)) return false;
        String semDv = l.substring(0, 4) + l.substring(33, 47) + l.substring(4, 9) + l.substring(10, 20) + l.substring(21, 31);
        return dvBoletoGeral(semDv) == digito(l, 32);
    }

    // arrecadação: começa em 8; cada bloco de 12 tem 11 dígitos e o DV; o 3º dígito define o módulo (6/7 = 10; 8/9 = 11)
    static boolean validarArrecadacao(String l) {
        if (l.charAt(0) != '8') return false;
        char identificador = l.charAt(2);
        if (identificador < '6' || identificador > '9') return true; // módulo não identificável: só tamanho
        boolean modulo10 = identificador == '6' || identificador == '7';
        for (int bloco = 0; bloco < 4; bloco++) {
            String campo = l.substring(bloco * 12, bloco * 12 + 11);
            int dv = modulo10 ? dvModulo10(campo) : dvArrecadacaoModulo11(campo);
            if (dv != digito(l, bloco * 12 + 11)) return false;
        }
        return true;
    }

    // módulo 10: pesos 2,1,2,1... da direita para a esquerda; produto maior que 9 soma os algarismos
    static int dvModulo10(String campo) {
        int soma = 0;
        int peso = 2;
        for (int i = campo.length() - 1; i >= 0; i--) {
            int produto = (campo.charAt(i) - '0') * peso;
            soma += produto > 9 ? produto - 9 : produto;
            peso = peso == 2 ? 1 : 2;
        }
        return (10 - soma % 10) % 10;
    }

    // DV geral do boleto: módulo 11 com pesos 2 a 9; resto que daria 0, 10 ou 11 vira 1
    static int dvBoletoGeral(String codigo) {
        int dv = 11 - somaModulo11(codigo) % 11;
        return dv == 0 || dv == 10 || dv == 11 ? 1 : dv;
    }

    // DV de arrecadação em módulo 11: resto 0 ou 1 vira 0
    static int dvArrecadacaoModulo11(String campo) {
        int resto = somaModulo11(campo) % 11;
        return resto == 0 || resto == 1 ? 0 : 11 - resto;
    }

    private static int somaModulo11(String campo) {
        int soma = 0;
        int peso = 2;
        for (int i = campo.length() - 1; i >= 0; i--) {
            soma += (campo.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        return soma;
    }

    private static int digito(String texto, int posicao) {
        return texto.charAt(posicao) - '0';
    }
}
