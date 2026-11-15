package com.manager.app.financeiro;

// busca por CPF/CNPJ: quem digita 111.444.777-35 também encontra o documento guardado sem máscara (11144477735)
public final class BuscaDocumento {

    private BuscaDocumento() {}

    // só dígitos quando a entrada é um documento mascarado (dígitos, ponto, traço, barra e espaço, com ao menos uma máscara); senão null
    public static String digitos(String termo) {
        if (termo == null) return null;
        String t = termo.trim();
        if (t.isEmpty() || !t.chars().allMatch(ch -> Character.isDigit(ch) || ch == '.' || ch == '-' || ch == '/' || ch == ' ')) return null;
        String digitos = t.replaceAll("[^0-9]", "");
        return digitos.isEmpty() || digitos.equals(t) ? null : digitos;
    }

    // padrão like (contém) com os dígitos, ou null
    public static String padrao(String termo) {
        String d = digitos(termo);
        return d == null ? null : "%" + d + "%";
    }
}
