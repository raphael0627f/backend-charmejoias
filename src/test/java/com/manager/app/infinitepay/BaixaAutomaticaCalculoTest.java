package com.manager.app.infinitepay;

import com.manager.app.financeiro.SituacaoContaReceber;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BaixaAutomaticaCalculoTest {

    private static final JsonMapper M = JsonMapper.builder().build();

    private static BaixaAutomaticaCalculo.Payload ler(String json) {
        return BaixaAutomaticaCalculo.ler(json, M);
    }

    private static final String COMPLETO = "{\"event\":\"paid\",\"invoice_slug\":\"s\",\"amount\":1500,\"paid_amount\":1510,\"capture_method\":\"pix\",\"transaction_nsu\":\"T\",\"order_nsu\":\"O\"}";

    @Test
    void leitura() {
        BaixaAutomaticaCalculo.Payload p = ler(COMPLETO);
        assertTrue(p.faltando().isEmpty());
        assertEquals(1510L, p.paidAmount());
        assertEquals(BaixaAutomaticaCalculo.Tipo.PAGAMENTO, BaixaAutomaticaCalculo.classificar(p));
        assertEquals(1500L, ler(COMPLETO.replace("1500", "\"1500\"")).amount()); // centavos em texto também valem
        assertNull(ler(COMPLETO.replace("1500", "15.5")).amount()); // decimal não é centavo inteiro
    }

    @Test
    void naoJsonEFaltandoCampoSaoInesperados() {
        assertEquals(BaixaAutomaticaCalculo.Tipo.INESPERADO, BaixaAutomaticaCalculo.classificar(ler("xyz")));
        assertEquals(BaixaAutomaticaCalculo.Tipo.INESPERADO, BaixaAutomaticaCalculo.classificar(ler("[1]")));
        assertFalse(ler("{\"event\":\"paid\"}").faltando().isEmpty());
        assertEquals(BaixaAutomaticaCalculo.Tipo.INESPERADO, BaixaAutomaticaCalculo.classificar(ler("{\"event\":\"novo_evento\"}")));
    }

    @Test
    void semNomeDeEventoMasComTodosOsCamposTrataComoPagamento() {
        assertEquals(BaixaAutomaticaCalculo.Tipo.PAGAMENTO, BaixaAutomaticaCalculo.classificar(ler(COMPLETO.replace("\"event\":\"paid\",", ""))));
    }

    @Test
    void sinaisEInformativos() {
        assertEquals(BaixaAutomaticaCalculo.Tipo.SINAL, BaixaAutomaticaCalculo.classificar(ler(COMPLETO.replace("paid\"", "refunded\""))));
        assertEquals(BaixaAutomaticaCalculo.Tipo.IGNORADO, BaixaAutomaticaCalculo.classificar(ler(COMPLETO.replace("\"paid\"", "\"created\""))));
    }

    private static BaixaAutomaticaCalculo.Decisao decidir(StatusLinkPagamento st, SituacaoContaReceber sit, String saldo, long amount, long pago, boolean forma, boolean bancoAtivo) {
        return BaixaAutomaticaCalculo.decidir(new BaixaAutomaticaCalculo.LinkInfo(st, ModoLinkPagamento.AUTOMATICO, new BigDecimal("15.00"), null, bancoAtivo),
                new BaixaAutomaticaCalculo.TituloInfo(sit, new BigDecimal(saldo)), new BaixaAutomaticaCalculo.Verificacao(true, true, amount, pago), "T", forma);
    }

    @Test
    void decisoes() {
        assertEquals(BaixaAutomaticaCalculo.Acao.BAIXAR, decidir(StatusLinkPagamento.ATIVO, SituacaoContaReceber.ABERTO, "15.00", 1500, 1500, true, true).acao());
        assertEquals(new BigDecimal("0.10"), decidir(StatusLinkPagamento.ATIVO, SituacaoContaReceber.ABERTO, "15.00", 1500, 1510, true, true).taxaCliente());
        assertEquals(BaixaAutomaticaCalculo.Acao.REVISAO, decidir(StatusLinkPagamento.ATIVO, SituacaoContaReceber.ABERTO, "15.00", 1500, 1499, true, true).acao());
        assertEquals(BaixaAutomaticaCalculo.Acao.REVISAO, decidir(StatusLinkPagamento.ATIVO, SituacaoContaReceber.ABERTO, "15.00", 1600, 1600, true, true).acao());
        assertEquals(BaixaAutomaticaCalculo.Acao.REVISAO, decidir(StatusLinkPagamento.ATIVO, SituacaoContaReceber.RECEBIDO, "0.00", 1500, 1500, true, true).acao());
        assertEquals(BaixaAutomaticaCalculo.Acao.REVISAO, decidir(StatusLinkPagamento.ATIVO, SituacaoContaReceber.CANCELADO, "0.00", 1500, 1500, true, true).acao());
        assertEquals(BaixaAutomaticaCalculo.Acao.REVISAO, decidir(StatusLinkPagamento.NAO_UTILIZAR, SituacaoContaReceber.ABERTO, "15.00", 1500, 1500, true, true).acao());
        assertEquals(BaixaAutomaticaCalculo.Acao.REVISAO, decidir(StatusLinkPagamento.ATIVO, SituacaoContaReceber.ABERTO, "10.00", 1500, 1500, true, true).acao());
        assertEquals(BaixaAutomaticaCalculo.Acao.REVISAO, decidir(StatusLinkPagamento.ATIVO, SituacaoContaReceber.ABERTO, "15.00", 1500, 1500, false, true).acao());
        assertEquals(BaixaAutomaticaCalculo.Acao.REVISAO, decidir(StatusLinkPagamento.ATIVO, SituacaoContaReceber.ABERTO, "15.00", 1500, 1500, true, false).acao());
    }

    @Test
    void mesmaTransacaoJaPagaEIgnorada() {
        var link = new BaixaAutomaticaCalculo.LinkInfo(StatusLinkPagamento.PAGO_AUTOMATICO, ModoLinkPagamento.AUTOMATICO, new BigDecimal("15.00"), "T", true);
        var d = BaixaAutomaticaCalculo.decidir(link, new BaixaAutomaticaCalculo.TituloInfo(SituacaoContaReceber.RECEBIDO, BigDecimal.ZERO),
                new BaixaAutomaticaCalculo.Verificacao(true, true, 1500, 1500), "T", true);
        assertEquals(BaixaAutomaticaCalculo.Acao.IGNORAR, d.acao());
    }

    @Test
    void observacaoMontadaPeloBackend() {
        String o = BaixaAutomaticaCalculo.observacao(ler(COMPLETO.replace("pix", "credit_card").replace("\"capture", "\"installments\":3,\"capture")), new BigDecimal("0.10"));
        assertTrue(o.contains("baixa automática") && o.contains("cartão 3x") && o.contains("R$ 0,10") && o.contains("NSU T"), o);
    }
}
