package com.manager.app.financeiro;

import com.manager.app.shared.exception.ArquivoMuitoGrandeException;
import com.manager.app.shared.exception.ItemInvalidoException;

import java.util.Locale;

// validação de anexos: tamanho, tipo pela ASSINATURA do arquivo (e não só pela extensão) e nome higienizado
public final class AnexoValidacao {

    public static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;
    public static final int MAXIMO_POR_TITULO = 10;
    static final int NOME_MAXIMO = 120;

    private AnexoValidacao() {}

    public record Validado(String tipoConteudo, String extensao, String nome) {}

    public static Validado validar(byte[] conteudo, String nomeOriginal) {
        if (conteudo == null || conteudo.length == 0) throw new ItemInvalidoException("O arquivo é obrigatório e não pode estar vazio.");
        if (conteudo.length > TAMANHO_MAXIMO) throw new ArquivoMuitoGrandeException("O arquivo excede o tamanho máximo permitido de 5 MB.");
        String tipo = detectar(conteudo);
        if (tipo == null) throw new ItemInvalidoException("Tipo de arquivo não permitido. Envie um arquivo PDF, JPG ou PNG.");
        String nome = sanitizarNome(nomeOriginal);
        String extensao = extensao(nome);
        boolean confere = switch (tipo) {
            case "application/pdf" -> extensao.equals("pdf");
            case "image/jpeg" -> extensao.equals("jpg") || extensao.equals("jpeg");
            default -> extensao.equals("png");
        };
        if (!confere) throw new ItemInvalidoException("A extensão do arquivo não corresponde ao seu conteúdo. Envie um arquivo PDF, JPG ou PNG válido.");
        return new Validado(tipo, extensao, nome);
    }

    // application/pdf, image/jpeg, image/png ou nulo
    static String detectar(byte[] b) {
        if (b.length >= 5 && b[0] == 0x25 && b[1] == 0x50 && b[2] == 0x44 && b[3] == 0x46 && b[4] == 0x2D) return "application/pdf";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47 && b[4] == 0x0D && b[5] == 0x0A
                && b[6] == 0x1A && b[7] == 0x0A) return "image/png";
        return null;
    }

    // remove o caminho (barras e barras invertidas) e caracteres especiais; limita o tamanho preservando a extensão
    public static String sanitizarNome(String original) {
        String nome = original == null ? "" : original;
        int corte = Math.max(nome.lastIndexOf('/'), nome.lastIndexOf((char) 92));
        if (corte >= 0) nome = nome.substring(corte + 1);
        StringBuilder sb = new StringBuilder();
        for (char c : nome.toCharArray()) {
            boolean permitido = Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == ' ' || c == '(' || c == ')';
            sb.append(permitido ? c : '_');
        }
        String limpo = sb.toString().trim();
        while (limpo.startsWith(".")) limpo = limpo.substring(1);
        if (limpo.isEmpty()) limpo = "arquivo";
        if (limpo.length() > NOME_MAXIMO) {
            String ext = extensao(limpo);
            int sufixo = ext.isEmpty() ? 0 : ext.length() + 1;
            String base = limpo.substring(0, limpo.length() - sufixo);
            limpo = base.substring(0, Math.min(base.length(), NOME_MAXIMO - sufixo)) + (ext.isEmpty() ? "" : "." + ext);
        }
        return limpo;
    }

    static String extensao(String nome) {
        int ponto = nome.lastIndexOf('.');
        return ponto < 0 || ponto == nome.length() - 1 ? "" : nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
    }
}
