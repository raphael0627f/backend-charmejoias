package com.manager.app.infinitepay;

import com.manager.app.financeiro.SituacaoContaReceber;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// regras puras da baixa automática (sem banco nem rede): leitura FECHADA do payload do webhook, classificação do evento e decisão
// entre BAIXAR, REVISAO e IGNORAR. Qualquer coisa inesperada vai para REVISAO: nunca se baixa por suposição.
public final class BaixaAutomaticaCalculo {

    private BaixaAutomaticaCalculo() {
    }

    public enum Acao { BAIXAR, REVISAO, IGNORAR }

    public enum Tipo { PAGAMENTO, SINAL, IGNORADO, INESPERADO }

    // campos lidos do payload; faltando lista os obrigatórios ausentes/inválidos para um pagamento
    public record Payload(boolean jsonValido, String evento, String orderNsu, String transactionNsu, String slug, Long amount, Long paidAmount,
                          String captureMethod, Integer installments, List<String> faltando) {
    }

    // desconto: diferença entre o valor do link e o pago, quando o link tem desconto de PIX/cartão e o pago é exatamente o valor com desconto
    public record Decisao(Acao acao, MotivoRevisao codigo, String motivo, BigDecimal taxaCliente, BigDecimal desconto) {
        public Decisao(Acao acao, MotivoRevisao codigo, String motivo, BigDecimal taxaCliente) {
            this(acao, codigo, motivo, taxaCliente, BigDecimal.ZERO);
        }

        static Decisao baixar(BigDecimal taxa, BigDecimal desconto) {
            return new Decisao(Acao.BAIXAR, null, null, taxa, desconto);
        }

        static Decisao revisao(MotivoRevisao codigo, String motivo) {
            return new Decisao(Acao.REVISAO, codigo, motivo, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        static Decisao ignorar(String motivo) {
            return new Decisao(Acao.IGNORAR, null, motivo, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    // motivo da decisão "pagamento não confirmado": o reprocessamento manual o reconhece para manter o item em revisão
    public static final String MOTIVO_NAO_CONFIRMADO = "O pagamento não foi confirmado pela InfinitePay.";

    // descontoPix/descontoCartao: percentuais configurados no link (nulo = sem desconto); meio: capture_method confirmado (pix, credit_card)
    public record LinkInfo(StatusLinkPagamento status, ModoLinkPagamento modo, BigDecimal valor, String transactionNsuPago, boolean bancoAtivo,
                           BigDecimal descontoPix, BigDecimal descontoCartao, String meio) {
        public LinkInfo(StatusLinkPagamento status, ModoLinkPagamento modo, BigDecimal valor, String transactionNsuPago, boolean bancoAtivo) {
            this(status, modo, valor, transactionNsuPago, bancoAtivo, null, null, null);
        }
    }

    // valor esperado (em centavos) do link com o desconto do meio de pagamento; nulo quando o meio não tem desconto configurado
    public static Long valorComDescontoCentavos(long amount, String meio, BigDecimal descontoPix, BigDecimal descontoCartao) {
        BigDecimal pct = meio == null ? null : switch (meio.toLowerCase(Locale.ROOT)) {
            case "pix" -> descontoPix;
            case "credit_card" -> descontoCartao;
            default -> null;
        };
        if (pct == null || pct.signum() <= 0) return null;
        return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(100).subtract(pct)).movePointLeft(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public record TituloInfo(SituacaoContaReceber situacao, BigDecimal saldoEmAberto) {
    }

    public record Verificacao(boolean success, boolean paid, long amount, long paidAmount) {
    }

    // ---------- leitura do payload ----------

    public static Payload ler(String corpo, ObjectMapper mapper) {
        JsonNode raiz = null;
        try {
            raiz = corpo == null || corpo.isBlank() ? null : mapper.readTree(corpo);
        } catch (RuntimeException e) {
            raiz = null;
        }
        if (raiz == null || !raiz.isObject()) {
            return new Payload(false, null, null, null, null, null, null, null, null, List.of("payload não é um objeto JSON"));
        }
        String evento = texto(raiz, "event");
        if (evento == null) evento = texto(raiz, "type");
        if (evento == null) evento = texto(raiz, "evento");
        String slug = texto(raiz, "invoice_slug");
        if (slug == null) slug = texto(raiz, "slug");
        Long amount = inteiro(raiz, "amount");
        Long paid = inteiro(raiz, "paid_amount");
        Integer parcelas = inteiro(raiz, "installments") == null ? null : inteiro(raiz, "installments").intValue();
        Payload p = new Payload(true, evento == null ? null : evento.toLowerCase(Locale.ROOT), texto(raiz, "order_nsu"), texto(raiz, "transaction_nsu"), slug, amount,
                paid, texto(raiz, "capture_method"), parcelas, List.of());
        List<String> faltando = new ArrayList<>();
        if (p.orderNsu() == null) faltando.add("order_nsu");
        if (p.transactionNsu() == null) faltando.add("transaction_nsu");
        if (p.slug() == null) faltando.add("invoice_slug");
        if (amount == null || amount <= 0) faltando.add("amount");
        if (paid == null || paid <= 0) faltando.add("paid_amount");
        if (p.captureMethod() == null) faltando.add("capture_method");
        return new Payload(true, p.evento(), p.orderNsu(), p.transactionNsu(), p.slug(), amount, paid, p.captureMethod(), parcelas, faltando);
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode v = no.get(campo);
        if (v == null || v.isNull() || v.isObject() || v.isArray()) return null;
        String s = v.asString();
        return s == null || s.isBlank() ? null : s.trim();
    }

    // aceita número inteiro ou texto numérico inteiro (centavos); outra coisa = ausente
    private static Long inteiro(JsonNode no, String campo) {
        JsonNode v = no.get(campo);
        if (v == null || v.isNull()) return null;
        try {
            if (v.isIntegralNumber()) return v.asLong();
            if (v.isString()) return Long.parseLong(v.asString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    // ---------- classificação do evento ----------

    // paid/settled = pagamento (a confirmação final é o payment_check). Sem nome de evento mas com todos os campos de pagamento: tratado como pagamento
    // (o webhook documentado do pagamento aprovado não traz o nome do evento); o payment_check continua sendo a palavra final.
    public static Tipo classificar(Payload p) {
        if (!p.jsonValido()) return Tipo.INESPERADO;
        String e = p.evento();
        if (e == null || e.equals("desconhecido")) return p.faltando().isEmpty() ? Tipo.PAGAMENTO : Tipo.INESPERADO;
        return switch (e) {
            case "paid", "settled" -> Tipo.PAGAMENTO;
            case "canceled", "cancelled", "refunded", "payment_failed", "failed" -> Tipo.SINAL;
            case "created" -> Tipo.IGNORADO;
            default -> Tipo.INESPERADO;
        };
    }

    // aviso com order_nsu mas sem transaction_nsu: o receptor o guarda com a chave "sha256:<resumo do corpo>"
    public static boolean semIdentificadorDeTransacao(Payload p) {
        return p.jsonValido() && p.orderNsu() != null && p.transactionNsu() == null;
    }

    public static final String PREFIXO_RESUMO = "sha256:";

    public static String motivoSinal(String evento) {
        return switch (evento == null ? "" : evento) {
            case "refunded" -> "A InfinitePay informou ESTORNO (refunded) de um pagamento. Nenhuma ação automática foi feita: confira o recebimento e, se for o caso, estorne-o manualmente.";
            case "canceled", "cancelled" -> "A InfinitePay informou o CANCELAMENTO (canceled) da cobrança. Nenhuma ação automática foi feita.";
            default -> "A InfinitePay informou falha no pagamento (" + (evento == null || evento.isBlank() ? "payment_failed" : evento) + "). Nenhuma ação automática foi feita.";
        };
    }

    // ---------- decisão ----------

    public static long centavos(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    // forma de pagamento nula = meio de captura sem forma mapeada
    public static Decisao decidir(LinkInfo link, TituloInfo titulo, Verificacao v, String transactionNsu, boolean formaMapeada) {
        if (link.modo() != ModoLinkPagamento.AUTOMATICO) {
            return Decisao.revisao(MotivoRevisao.LINK_MANUAL, "O pagamento refere-se a um link de baixa manual. Registre o recebimento manualmente, se for o caso.");
        }
        if (!v.success() || !v.paid()) {
            return Decisao.ignorar(MOTIVO_NAO_CONFIRMADO);
        }
        if (titulo.situacao() == SituacaoContaReceber.CANCELADO) {
            return Decisao.revisao(MotivoRevisao.TITULO_CANCELADO, "Pagamento recebido após o cancelamento do título.");
        }
        if (link.status() == StatusLinkPagamento.PAGO_AUTOMATICO && transactionNsu.equals(link.transactionNsuPago())) {
            return Decisao.ignorar("Pagamento já registrado anteriormente (mesma transação).");
        }
        if (titulo.situacao() == SituacaoContaReceber.RECEBIDO) {
            return Decisao.revisao(MotivoRevisao.DUPLICIDADE, "O título já estava quitado: possível pagamento em duplicidade.");
        }
        if (link.status() != StatusLinkPagamento.ATIVO) {
            return Decisao.revisao(MotivoRevisao.DUPLICIDADE, "O link de pagamento já não estava ativo (situação " + link.status().name() + "): possível pagamento em duplicidade ou link obsoleto.");
        }
        if (v.amount() != centavos(link.valor())) {
            return Decisao.revisao(MotivoRevisao.VALOR_DIVERGENTE, "Valor da cobrança confirmado pela InfinitePay (" + moeda(v.amount()) + ") diverge do valor do link (" + moeda(centavos(link.valor())) + ").");
        }
        BigDecimal desconto = BigDecimal.ZERO;
        if (v.paidAmount() < v.amount()) {
            // pago exatamente o valor com o desconto de PIX/cartão configurado no link: baixa direta (principal = valor do link, desconto = diferença)
            Long esperado = valorComDescontoCentavos(v.amount(), link.meio(), link.descontoPix(), link.descontoCartao());
            if (esperado != null && esperado == v.paidAmount()) {
                desconto = BigDecimal.valueOf(v.amount() - v.paidAmount()).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY);
            } else {
                return Decisao.revisao(MotivoRevisao.VALOR_MENOR, "Valor pago (" + moeda(v.paidAmount()) + ") menor que o valor da cobrança (" + moeda(v.amount()) + "): possível desconto de PIX ou cartão.");
            }
        }
        if (titulo.saldoEmAberto().compareTo(link.valor()) < 0) {
            return Decisao.revisao(MotivoRevisao.SALDO_INSUFICIENTE, "O saldo em aberto do título (" + moeda(centavos(titulo.saldoEmAberto())) + ") é menor que o valor pago pelo link.");
        }
        if (!formaMapeada) {
            return Decisao.revisao(MotivoRevisao.FORMA_NAO_MAPEADA, "O meio de pagamento utilizado não tem forma de pagamento correspondente na configuração.");
        }
        if (!link.bancoAtivo()) {
            return Decisao.revisao(MotivoRevisao.BANCO_DESATIVADO, "O banco de destino do link está desativado.");
        }
        BigDecimal taxa = v.paidAmount() > v.amount() ? BigDecimal.valueOf(v.paidAmount() - v.amount()).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY)
                : BigDecimal.ZERO.setScale(2);
        return Decisao.baixar(taxa, desconto);
    }

    // texto da baixa (descrição/observação do recebimento): deixa claro que foi automática e informa a taxa do cliente
    public static String observacao(Payload p, BigDecimal taxa, BigDecimal desconto) {
        String base = observacao(p, taxa);
        if (desconto == null || desconto.signum() <= 0) return base;
        String extra = " Desconto concedido pelo link: " + moeda(centavos(desconto)) + ".";
        return (base.length() + extra.length() > 500 ? base.substring(0, 500 - extra.length()) : base) + extra;
    }

    public static String observacao(Payload p, BigDecimal taxa) {
        String meio = p.captureMethod() == null ? "" : p.captureMethod().toLowerCase(Locale.ROOT);
        String forma;
        if (meio.equals("pix")) forma = "PIX";
        else if (meio.equals("credit_card")) forma = "cartão" + (p.installments() != null && p.installments() > 1 ? " " + p.installments() + "x" : "");
        else forma = p.captureMethod() == null ? "meio não informado" : p.captureMethod();
        String texto = "Pago pelo link InfinitePay (baixa automática), " + forma + ".";
        if (taxa != null && taxa.signum() > 0) texto += " Taxa paga pelo cliente: " + moeda(centavos(taxa)) + ".";
        texto += " NSU " + p.transactionNsu() + ".";
        return texto.length() > 500 ? texto.substring(0, 500) : texto;
    }

    static String moeda(long centavos) {
        return "R$ " + String.format(new Locale("pt", "BR"), "%,.2f", centavos / 100.0);
    }
}
