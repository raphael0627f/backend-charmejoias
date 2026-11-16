package com.manager.app.infinitepay;

import com.manager.app.shared.exception.ItemInvalidoException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

// regras puras do link de pagamento (sem acesso a banco nem a rede): validações, normalização e montagem do pedido à InfinitePay
public final class LinkPagamentoCalculo {

    public static final BigDecimal VALOR_MINIMO = new BigDecimal("1.00");
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    public static final int RESPOSTA_MAXIMA = 1000;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public static final String TEXTO_WHATSAPP_PADRAO = "Olá, {cliente}! Segue o link para pagamento de {valor} referente a {descricao}: {link} Válido {validade}. Em caso de dúvida, estamos à disposição.";
    public static final String TEXTO_EMAIL_PADRAO = "Prezado(a) {cliente}, segue o link para pagamento de {valor} referente a {descricao}: {link} Válido {validade}. Em caso de dúvida, estamos à disposição.";

    private LinkPagamentoCalculo() {
    }

    // dados do cliente efetivamente enviados (cada campo só existe quando válido)
    public record Cliente(String nome, String email, String telefone) {
        public boolean vazio() {
            return nome == null && email == null && telefone == null;
        }
    }

    // dados já validados e normalizados de um link (também é o que se grava e o que se compara no duplo clique)
    public record Dados(BigDecimal valor, String descricao, LocalDate validade, boolean pagamentoUnico, int taxaParcelamentoAssumida,
                        boolean pixHabilitado, BigDecimal descontoPix, BigDecimal descontoCartao, boolean exigeEndereco, Cliente cliente) {
    }

    // ---------- validações ----------

    // valor de 1,00 até o saldo em aberto; ausente = o próprio saldo
    public static BigDecimal validarValor(BigDecimal valor, BigDecimal saldoEmAberto) {
        BigDecimal saldo = saldoEmAberto == null ? BigDecimal.ZERO : saldoEmAberto.setScale(2, RoundingMode.HALF_UP);
        BigDecimal v = valor == null ? saldo : valor;
        if (v.stripTrailingZeros().scale() > 2) throw new ItemInvalidoException("O valor do link deve ter no máximo duas casas decimais.");
        v = v.setScale(2, RoundingMode.HALF_UP);
        if (v.compareTo(VALOR_MINIMO) < 0) {
            throw new ItemInvalidoException("O valor do link de pagamento deve ser de, no mínimo, R$ 1,00.");
        }
        if (v.compareTo(saldo) > 0) {
            throw new ItemInvalidoException("O valor do link não pode ser superior ao saldo em aberto do título (" + moeda(saldo) + ").");
        }
        return v;
    }

    // futura (a partir de amanhã) e no máximo 1 ano; nula = sem validade
    public static LocalDate validarValidade(LocalDate validade, LocalDate hoje) {
        if (validade == null) return null;
        if (!validade.isAfter(hoje)) throw new ItemInvalidoException("A validade do link deve ser uma data futura.");
        if (validade.isAfter(hoje.plusYears(1))) throw new ItemInvalidoException("A validade do link não pode ser superior a 1 ano.");
        return validade;
    }

    public static int validarTaxa(Integer taxa) {
        int t = taxa == null ? 0 : taxa;
        if (t < 0 || t > 12) throw new ItemInvalidoException("A quantidade de parcelas cuja taxa é assumida deve estar entre 0 e 12.");
        return t;
    }

    // ausente ou 0 = sem desconto; senão maior que 0 e menor que 100
    public static BigDecimal validarDesconto(String nome, BigDecimal desconto) {
        if (desconto == null || desconto.signum() == 0) return BigDecimal.ZERO.setScale(2);
        if (desconto.signum() < 0 || desconto.compareTo(new BigDecimal("100")) >= 0) {
            throw new ItemInvalidoException("O desconto " + nome + " deve ser maior que 0 e menor que 100.");
        }
        return desconto.setScale(2, RoundingMode.HALF_UP);
    }

    public static String validarDescricao(String descricao, String padrao) {
        String d = descricao == null ? "" : descricao.trim();
        if (d.isEmpty()) d = padrao == null ? "" : padrao.trim();
        if (d.isEmpty()) throw new ItemInvalidoException("A descrição do link é obrigatória.");
        if (d.length() > 200) throw new ItemInvalidoException("A descrição do link deve ter no máximo 200 caracteres.");
        return d;
    }

    // ---------- dados do cliente (só o que for válido é enviado) ----------

    // formato básico: um único @, texto antes, domínio com ponto e sem espaços
    public static boolean emailValido(String email) {
        if (email == null) return false;
        String e = email.trim();
        if (e.length() < 6 || e.length() > 200 || e.contains(" ")) return false;
        int arroba = e.indexOf('@');
        if (arroba < 1 || arroba != e.lastIndexOf('@')) return false;
        String dominio = e.substring(arroba + 1);
        int ponto = dominio.lastIndexOf('.');
        return ponto > 0 && ponto < dominio.length() - 2 && !dominio.startsWith(".") && !dominio.contains("..");
    }

    // devolve +55DDDNÚMERO (10 ou 11 dígitos nacionais) ou null quando inválido
    public static String normalizarTelefone(String telefone) {
        if (telefone == null) return null;
        StringBuilder digitos = new StringBuilder();
        for (char c : telefone.toCharArray()) if (c >= '0' && c <= '9') digitos.append(c);
        String d = digitos.toString();
        if ((d.length() == 12 || d.length() == 13) && d.startsWith("55")) d = d.substring(2);
        if (d.length() != 10 && d.length() != 11) return null;
        if (d.charAt(0) == '0' || d.charAt(1) == '0') return null; // DDD inexistente
        if (d.length() == 11 && d.charAt(2) != '9') return null;  // celular começa com 9
        if (d.length() == 10 && (d.charAt(2) == '0' || d.charAt(2) == '1')) return null;
        return "+55" + d;
    }

    // celular preferido; sem celular válido, o telefone
    public static String telefoneDoCadastro(String celular, String telefone) {
        String c = normalizarTelefone(celular);
        return c != null ? c : normalizarTelefone(telefone);
    }

    public static Cliente normalizarCliente(String nome, String email, String telefone) {
        String n = nome == null ? null : nome.trim();
        if (n != null && n.isEmpty()) n = null;
        if (n != null && n.length() > 200) n = n.substring(0, 200);
        String e = emailValido(email) ? email.trim() : null;
        return new Cliente(n, e, normalizarTelefone(telefone));
    }

    // ---------- pedido à InfinitePay ----------

    public static long centavos(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    // fim do dia informado, no fuso de São Paulo, em ISO 8601 com deslocamento
    public static String validadeIso(LocalDate validade) {
        return validade.atTime(23, 59, 59).atZone(FUSO).format(ISO);
    }

    public static Map<String, Object> montarPedido(String handle, String orderNsu, Dados d) {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("handle", handle);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("quantity", 1);
        item.put("price", centavos(d.valor()));
        item.put("description", d.descricao());
        pedido.put("items", List.of(item));
        pedido.put("order_nsu", orderNsu);
        if (d.cliente() != null && !d.cliente().vazio()) {
            Map<String, Object> cliente = new LinkedHashMap<>();
            if (d.cliente().nome() != null) cliente.put("name", d.cliente().nome());
            if (d.cliente().email() != null) cliente.put("email", d.cliente().email());
            if (d.cliente().telefone() != null) cliente.put("phone_number", d.cliente().telefone());
            pedido.put("customer", cliente);
        }
        Map<String, Object> opcoes = new LinkedHashMap<>();
        opcoes.put("assured_installments", d.taxaParcelamentoAssumida());
        opcoes.put("require_delivery_address", d.exigeEndereco());
        opcoes.put("single_payment", d.pagamentoUnico());
        if (d.validade() != null) opcoes.put("valid_until", validadeIso(d.validade()));
        // o cartão fica sempre ligado; só o PIX é desligado explicitamente (ligado = padrão da InfinitePay)
        if (!d.pixHabilitado()) opcoes.put("payment_methods_enabled", List.of("credit_card"));
        Map<String, Object> descontos = new LinkedHashMap<>();
        if (d.descontoPix().signum() > 0) descontos.put("pix", d.descontoPix().doubleValue());
        if (d.descontoCartao().signum() > 0) descontos.put("credit_card", d.descontoCartao().doubleValue());
        if (!descontos.isEmpty()) opcoes.put("discounts", descontos);
        pedido.put("options", opcoes);
        return pedido;
    }

    private static final List<String> CHAVES_URL = List.of("url", "link", "checkout_url", "payment_url", "link_url", "checkout_link", "payment_link", "invoice_url", "short_url");
    private static final List<String> CHAVES_AGREGADAS = List.of("data", "result", "response", "checkout");

    // a resposta não é documentada: aceita 'url' e variantes comuns (também dentro de data/result) e, como último recurso, texto simples com a URL
    public static String extrairUrl(JsonNode resposta, String textoBruto) {
        if (resposta != null && !resposta.isMissingNode() && !resposta.isNull()) {
            String achada = procurarUrl(resposta, 0);
            if (achada != null) return achada;
        }
        if (textoBruto != null) {
            String t = textoBruto.trim();
            if (urlHttp(t) && !t.contains(" ")) return t;
        }
        return null;
    }

    private static String procurarUrl(JsonNode no, int nivel) {
        if (nivel > 3 || no == null) return null;
        if (no.isString()) return urlHttp(no.asString()) ? no.asString().trim() : null;
        if (!no.isObject()) return null;
        for (String chave : CHAVES_URL) {
            JsonNode v = no.get(chave);
            if (v == null) continue;
            String achada = procurarUrl(v, nivel + 1);
            if (achada != null) return achada;
        }
        for (String agregado : CHAVES_AGREGADAS) {
            JsonNode v = no.get(agregado);
            String achada = v == null ? null : procurarUrl(v, nivel + 1);
            if (achada != null) return achada;
        }
        return null;
    }

    private static boolean urlHttp(String s) {
        if (s == null) return false;
        String t = s.trim().toLowerCase(Locale.ROOT);
        return (t.startsWith("https://") || t.startsWith("http://")) && t.length() > 12 && t.length() <= 1000;
    }

    // o corpo devolvido pela InfinitePay pode ecoar a chave: antes de ir para o log, a chave é substituída e o trecho é truncado
    public static String ocultarSegredo(String texto, String segredo, int max) {
        if (texto == null) return null;
        String t = segredo == null || segredo.isBlank() ? texto : texto.replace(segredo, "****");
        return truncar(t, max);
    }

    public static String truncar(String texto, int max) {
        if (texto == null) return null;
        return texto.length() <= max ? texto : texto.substring(0, max);
    }

    // ---------- textos ----------

    public static String moeda(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(valor).replace((char) 160, ' ');
    }

    public static String data(LocalDate d) {
        return d == null ? "" : DATA.format(d);
    }

    public static String ultimosCaracteres(String chave) {
        if (chave == null || chave.isBlank()) return null;
        if (chave.length() < 8) return "****";
        return "****" + chave.substring(chave.length() - 4);
    }

    public static String limparHandle(String handle) {
        if (handle == null) return null;
        String h = handle.trim();
        while (h.startsWith("$")) h = h.substring(1).trim();
        return h.isEmpty() ? null : h;
    }

    public static void validarHandle(String h) {
        if (h == null) return;
        if (h.length() > 60) throw new ItemInvalidoException("A InfiniteTag deve ter no máximo 60 caracteres.");
        for (char c : h.toCharArray()) {
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            if (!ok) throw new ItemInvalidoException("A InfiniteTag deve conter apenas letras, números, ponto, sublinhado e hífen.");
        }
    }

    public static String renderizar(String modelo, String padrao, String cliente, BigDecimal valor, String descricao, String link, LocalDate validade) {
        String t = modelo == null || modelo.isBlank() ? padrao : modelo;
        String quando = validade == null ? "sem data de expiração" : "até " + data(validade);
        return t.replace("{cliente}", cliente == null || cliente.isBlank() ? "cliente" : cliente)
                .replace("{valor}", moeda(valor))
                .replace("{descricao}", descricao == null ? "" : descricao)
                .replace("{link}", link == null ? "" : link)
                .replace("{validade}", quando);
    }

    // duplo clique: o link existente tem exatamente os mesmos dados do pedido?
    public static boolean mesmosDados(Dados d, ContaReceberLinkPagamento l) {
        Cliente c = d.cliente() == null ? new Cliente(null, null, null) : d.cliente();
        return d.valor().compareTo(l.getValor()) == 0
                && d.descricao().equals(l.getDescricao())
                && Objects.equals(d.validade(), l.getValidade())
                && d.pagamentoUnico() == l.isPagamentoUnico()
                && d.taxaParcelamentoAssumida() == l.getAssuredInstallments()
                && d.pixHabilitado() == l.isPixHabilitado()
                && d.descontoPix().compareTo(l.getDescontoPix()) == 0
                && d.descontoCartao().compareTo(l.getDescontoCartao()) == 0
                && d.exigeEndereco() == l.isExigeEndereco()
                && Objects.equals(c.nome(), l.getClienteNome())
                && Objects.equals(c.email(), l.getClienteEmail())
                && Objects.equals(c.telefone(), l.getClienteTelefone());
    }
}
