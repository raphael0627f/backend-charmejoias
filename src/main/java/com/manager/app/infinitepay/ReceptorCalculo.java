package com.manager.app.infinitepay;

import com.manager.app.shared.exception.ItemInvalidoException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

// regras puras do receptor na nuvem (sem rede nem banco): validação da URL, dos segredos e montagem do webhook do link.
// O webhook é gravado POR LINK na InfinitePay e não pode ser editado depois: uma URL errada inutiliza o link, por isso a validação é estrita.
public final class ReceptorCalculo {

    public static final int SEGREDO_MINIMO = 32;
    public static final int SEGREDO_MAXIMO = 200;
    private static final Pattern SEGREDO = Pattern.compile("[A-Za-z0-9_\\-]+");
    // eventos pedidos à InfinitePay (created é ruído; refunded/canceled/payment_failed só sinalizam). Nomes conforme a documentação; confirmar no primeiro uso real.
    public static final List<String> EVENTOS = List.of("paid", "settled", "canceled", "refunded", "payment_failed");

    private ReceptorCalculo() {
    }

    // https obrigatório (http só com permitirLocal, para testes locais), sem credenciais/caminho/consulta, fora de workers.dev e de endereços locais
    public static String validarUrl(String url, boolean permitirLocal) {
        if (url == null || url.isBlank()) throw new ItemInvalidoException("Informe a URL do receptor de pagamentos.");
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        URI uri;
        try {
            uri = URI.create(u);
        } catch (IllegalArgumentException e) {
            throw new ItemInvalidoException("A URL do receptor de pagamentos é inválida.");
        }
        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) throw new ItemInvalidoException("A URL do receptor de pagamentos é inválida.");
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty())) {
            throw new ItemInvalidoException("A URL do receptor deve conter apenas o endereço (por exemplo, https://pagamentos.exemplo.com.br), sem caminho nem parâmetros.");
        }
        if (!permitirLocal) {
            if (!esquema.equals("https")) throw new ItemInvalidoException("A URL do receptor de pagamentos deve usar HTTPS.");
            if (local(host)) throw new ItemInvalidoException("A URL do receptor não pode apontar para um endereço local.");
            if (host.endsWith("workers.dev")) {
                throw new ItemInvalidoException("Use o domínio próprio do receptor: o endereço workers.dev não é permitido, pois a URL do webhook não pode ser alterada depois de gerado o link.");
            }
        } else if (!esquema.equals("https") && !esquema.equals("http")) {
            throw new ItemInvalidoException("A URL do receptor de pagamentos é inválida.");
        }
        return u;
    }

    private static boolean local(String host) {
        return host.equals("localhost") || host.endsWith(".localhost") || host.equals("0.0.0.0") || host.equals("::1") || host.equals("[::1]")
                || host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.") || host.matches("172\\.(1[6-9]|2\\d|3[01])\\..*")
                || host.startsWith("169.254.") || !host.contains(".");
    }

    // segredo (token de entrada ou segredo do ERP): somente letras, números, '-' e '_', de 32 a 200 caracteres
    public static String validarSegredo(String nome, String valor) {
        String v = valor == null ? "" : valor.trim();
        if (v.length() < SEGREDO_MINIMO || v.length() > SEGREDO_MAXIMO || !SEGREDO.matcher(v).matches()) {
            throw new ItemInvalidoException(nome + " deve ter de " + SEGREDO_MINIMO + " a " + SEGREDO_MAXIMO + " caracteres, somente letras, números, '-' e '_'.");
        }
        return v;
    }

    public static int validarIntervalo(Integer minutos) {
        if (minutos == null || minutos < 1 || minutos > 60) throw new ItemInvalidoException("O intervalo de busca deve estar entre 1 e 60 minutos.");
        return minutos;
    }

    // endereço que a InfinitePay chamará: <receptor>/w/<token>
    public static String webhookUrl(String receptorUrl, String tokenEntrada) {
        return receptorUrl + "/w/" + tokenEntrada;
    }

    // objeto webhook do pedido de criação do link
    public static Map<String, Object> webhook(String receptorUrl, String tokenEntrada) {
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("url", webhookUrl(receptorUrl, tokenEntrada));
        w.put("events", EVENTOS);
        return w;
    }
}
