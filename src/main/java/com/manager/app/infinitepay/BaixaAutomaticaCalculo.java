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

    public record Decisao(Acao acao, String motivo, BigDecimal taxaCliente) {
        static Decisao baixar(BigDecimal taxa) {
            return new Decisao(Acao.BAIXAR, null, taxa);
        }

        static Decisao revisao(String motivo) {
            return new Decisao(Acao.REVISAO, motivo, BigDecimal.ZERO);
        }

        static Decisao ignorar(String motivo) {
            return new Decisao(Acao.IGNORAR, motivo, BigDecimal.ZERO);
        }
    }

    public record LinkInfo(StatusLinkPagamento status, ModoLinkPagamento modo, BigDecimal valor, String transactionNsuPago, boolean bancoAtivo) {
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
            case "canceled", "cancelled", "refunded", "payment_failed" -> Tipo.SINAL;
            case "created" -> Tipo.IGNORADO;
            default -> Tipo.INESPERADO;
        };
    }

    public static String motivoSinal(String evento) {
        return switch (evento == null ? "" : evento) {
            case "refunded" -> "A InfinitePay informou ESTORNO (refunded) de um pagamento. Nenhuma ação automática foi feita: confira o recebimento e, se for o caso, estorne-o manualmente.";
            case "canceled", "cancelled" -> "A InfinitePay informou o CANCELAMENTO (canceled) da cobrança. Nenhuma ação automática foi feita.";
            default -> "A InfinitePay informou falha no pagamento (payment_failed). Nenhuma ação automática foi feita.";
        };
    }

    // ---------- decisão ----------

    public static long centavos(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    // forma de pagamento nula = meio de captura sem forma mapeada
    public static Decisao decidir(LinkInfo link, TituloInfo titulo, Verificacao v, String transactionNsu, boolean formaMapeada) {
        if (link.modo() != ModoLinkPagamento.AUTOMATICO) {
            return Decisao.revisao("O pagamento refere-se a um link de baixa manual. Registre o recebimento manualmente, se for o caso.");
        }
        if (!v.success() || !v.paid()) {
            return Decisao.ignorar("O pagamento não foi confirmado pela InfinitePay (payment_check).");
        }
        if (titulo.situacao() == SituacaoContaReceber.CANCELADO) {
            return Decisao.revisao("Pagamento recebido após o cancelamento do título.");
        }
        if (link.status() == StatusLinkPagamento.PAGO_AUTOMATICO && transactionNsu.equals(link.transactionNsuPago())) {
            return Decisao.ignorar("Pagamento já registrado anteriormente (mesma transação).");
        }
        if (titulo.situacao() == SituacaoContaReceber.RECEBIDO) {
            return Decisao.revisao("O título já estava quitado: possível pagamento em duplicidade.");
        }
        if (link.status() != StatusLinkPagamento.ATIVO) {
            return Decisao.revisao("O link de pagamento já não estava ativo (situação " + link.status().name() + "): possível pagamento em duplicidade ou link obsoleto.");
        }
        if (v.amount() != centavos(link.valor())) {
            return Decisao.revisao("Valor da cobrança confirmado pela InfinitePay (" + moeda(v.amount()) + ") diverge do valor do link (" + moeda(centavos(link.valor())) + ").");
        }
        if (v.paidAmount() < v.amount()) {
            return Decisao.revisao("Valor pago (" + moeda(v.paidAmount()) + ") menor que o valor da cobrança (" + moeda(v.amount()) + "): possível desconto de PIX ou cartão.");
        }
        if (titulo.saldoEmAberto().compareTo(link.valor()) < 0) {
            return Decisao.revisao("O saldo em aberto do título (" + moeda(centavos(titulo.saldoEmAberto())) + ") é menor que o valor pago pelo link.");
        }
        if (!formaMapeada) {
            return Decisao.revisao("O meio de pagamento utilizado não tem forma de pagamento correspondente na configuração.");
        }
        if (!link.bancoAtivo()) {
            return Decisao.revisao("O banco de destino do link está desativado.");
        }
        BigDecimal taxa = BigDecimal.valueOf(v.paidAmount() - v.amount()).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY);
        return Decisao.baixar(taxa);
    }

    // texto da baixa (descrição/observação do recebimento): deixa claro que foi automática e informa a taxa do cliente
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
