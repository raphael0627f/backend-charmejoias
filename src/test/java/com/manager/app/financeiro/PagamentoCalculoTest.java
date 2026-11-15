package com.manager.app.financeiro;

import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagamentoCalculoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 14);

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    private static Banco banco(boolean ativo) {
        Banco b = new Banco();
        b.setId(1L);
        b.setNome("Caixa");
        b.setAtivo(ativo);
        return b;
    }

    private static FormaPagamento forma(boolean ativo) {
        FormaPagamento f = new FormaPagamento();
        f.setId(1L);
        f.setNome("PIX");
        f.setAtivo(ativo);
        return f;
    }

    private static ContaPagar titulo(String valor) {
        ContaPagar c = new ContaPagar();
        c.setId(10L);
        c.setNumero(7L);
        c.setDescricao("Aluguel");
        c.setValor(v(valor));
        c.setValorPago(BigDecimal.ZERO);
        c.setSituacao(SituacaoContaPagar.ABERTO);
        return c;
    }

    private static PagamentoCalculo.ResultadoPagamento pagar(ContaPagar t, String principal, String juros, String multa, String desconto) {
        return PagamentoCalculo.pagar(t, banco(true), forma(true), HOJE, v(principal), juros == null ? null : v(juros),
                multa == null ? null : v(multa), desconto == null ? null : v(desconto), null, null, HOJE);
    }

    @Test
    void valorTotalSomaJurosMultaEDescontaDesconto() {
        assertThat(PagamentoCalculo.valorTotal(v("1000"), v("12.50"), v("20"), v("5"))).isEqualByComparingTo("1027.50");
    }

    @Test
    void pagamentoParcialAbateSoOPrincipalEMarcaPagoParcial() {
        ContaPagar t = titulo("1000");
        PagamentoCalculo.ResultadoPagamento r = pagar(t, "400", "10", "5", "0");
        assertThat(t.getValorPago()).isEqualByComparingTo("400");
        assertThat(t.getSituacao()).isEqualTo(SituacaoContaPagar.PAGO_PARCIAL);
        assertThat(r.pagamento().getValorTotal()).isEqualByComparingTo("415");
        assertThat(r.movimentacao().getTipo()).isEqualTo(TipoMovimentacao.SAIDA);
        assertThat(r.movimentacao().getValor()).isEqualByComparingTo("415");
        assertThat(r.movimentacao().getOrigem()).isEqualTo(OrigemMovimentacao.PAGAMENTO_CONTA_PAGAR);
        assertThat(r.movimentacao().getDescricao()).contains("7").contains("Aluguel");
    }

    @Test
    void jurosMultaEDescontoNaoAlteramOValorDoTitulo() {
        ContaPagar t = titulo("1000");
        pagar(t, "1000", "50", "30", "20");
        assertThat(t.getValor()).isEqualByComparingTo("1000");
        assertThat(t.getValorPago()).isEqualByComparingTo("1000");
        assertThat(t.getSituacao()).isEqualTo(SituacaoContaPagar.PAGO);
    }

    @Test
    void duasPagamentosParciaisQuitamOTitulo() {
        ContaPagar t = titulo("100.00");
        pagar(t, "60.01", null, null, null);
        assertThat(t.getSituacao()).isEqualTo(SituacaoContaPagar.PAGO_PARCIAL);
        pagar(t, "39.99", null, null, null);
        assertThat(t.getValorPago()).isEqualByComparingTo("100.00");
        assertThat(t.getSituacao()).isEqualTo(SituacaoContaPagar.PAGO);
    }

    @Test
    void principalAcimaDoEmAbertoEhRecusado() {
        ContaPagar t = titulo("100");
        pagar(t, "70", null, null, null);
        assertThatThrownBy(() -> pagar(t, "30.01", null, null, null)).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("em aberto");
        assertThat(t.getValorPago()).isEqualByComparingTo("70");
    }

    @Test
    void descontoLimiteEhASomaDePrincipalJurosEMulta() {
        assertThatThrownBy(() -> pagar(titulo("100"), "100", "10", "5", "115.01")).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("desconto");
        // desconto igual à soma zera o total, que também é recusado
        assertThatThrownBy(() -> pagar(titulo("100"), "100", "10", "5", "115")).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("maior que zero");
        assertThat(pagar(titulo("100"), "100", "10", "5", "114.99").pagamento().getValorTotal()).isEqualByComparingTo("0.01");
    }

    @Test
    void valoresInvalidosSaoRecusados() {
        assertThatThrownBy(() -> pagar(titulo("100"), "0", null, null, null)).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> pagar(titulo("100"), "10", "-1", null, null)).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> pagar(titulo("100"), "10", null, "1.005", null)).isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void valoresAcimaDoTetoSaoRecusados() {
        assertThat(CalculoFinanceiro.validarValorPositivo(v("9999999.99"))).isEqualByComparingTo("9999999.99");
        assertThatThrownBy(() -> CalculoFinanceiro.validarValorPositivo(v("10000000.00"))).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("9.999.999,99");
        assertThatThrownBy(() -> CalculoFinanceiro.validarValorPositivo(v("999999999999.99"))).hasMessageContaining("não pode ser superior");
        assertThatThrownBy(() -> CalculoFinanceiro.validarTeto(v("-10000000"), "saldo inicial")).hasMessageContaining("saldo inicial");
        CalculoFinanceiro.validarTeto(v("-9999999.99"), "saldo inicial");
        ContaPagar t = titulo("9999999.99");
        assertThatThrownBy(() -> pagar(t, "100", "10000000", null, null)).hasMessageContaining("juros");
        assertThatThrownBy(() -> pagar(t, "100", null, "10000000", null)).hasMessageContaining("multa");
        assertThatThrownBy(() -> pagar(t, "100", null, null, "10000000")).hasMessageContaining("desconto");
        assertThat(pagar(t, "9999999.99", null, null, null).pagamento().getValorTotal()).isEqualByComparingTo("9999999.99");
    }

    @Test
    void tituloCanceladoOuPagoNaoRecebePagamento() {
        ContaPagar cancelado = titulo("100");
        cancelado.setSituacao(SituacaoContaPagar.CANCELADO);
        assertThatThrownBy(() -> pagar(cancelado, "10", null, null, null)).hasMessageContaining("cancelado");
        ContaPagar pago = titulo("100");
        pagar(pago, "100", null, null, null);
        assertThatThrownBy(() -> pagar(pago, "10", null, null, null)).hasMessageContaining("pago");
    }

    @Test
    void dataFuturaBancoEFormaInativosSaoRecusados() {
        assertThatThrownBy(() -> PagamentoCalculo.pagar(titulo("100"), banco(true), forma(true), HOJE.plusDays(1), v("10"), null, null, null, null, null, HOJE))
                .isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> PagamentoCalculo.pagar(titulo("100"), banco(false), forma(true), HOJE, v("10"), null, null, null, null, null, HOJE))
                .isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> PagamentoCalculo.pagar(titulo("100"), banco(true), forma(false), HOJE, v("10"), null, null, null, null, null, HOJE))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("inativa");
    }

    @Test
    void pagamentoEmLotePagaOSaldoIntegralDeCadaTitulo() {
        ContaPagar a = titulo("250.40");
        ContaPagar b = titulo("100");
        pagar(b, "30", null, null, null);
        ContaPagar c = titulo("10");
        c.setSituacao(SituacaoContaPagar.CANCELADO);
        assertThat(PagamentoCalculo.saldoIntegral(a)).isEqualByComparingTo("250.40");
        assertThat(PagamentoCalculo.saldoIntegral(b)).isEqualByComparingTo("70");
        assertThatThrownBy(() -> PagamentoCalculo.saldoIntegral(c)).isInstanceOf(ItemInvalidoException.class);
        pagar(b, PagamentoCalculo.saldoIntegral(b).toPlainString(), null, null, null);
        assertThat(b.getSituacao()).isEqualTo(SituacaoContaPagar.PAGO);
    }

    @Test
    void estornoParcialVoltaAParcialETotalVoltaAAberto() {
        ContaPagar t = titulo("100");
        PagamentoCalculo.ResultadoPagamento b1 = pagar(t, "40", null, null, null);
        PagamentoCalculo.ResultadoPagamento b2 = pagar(t, "60", "5", null, null);
        assertThat(t.getSituacao()).isEqualTo(SituacaoContaPagar.PAGO);

        MovimentacaoBanco inversa2 = PagamentoCalculo.estornar(b2.pagamento(), b2.movimentacao(), "Pagamento em duplicidade", null, HOJE, LocalDateTime.now());
        assertThat(t.getValorPago()).isEqualByComparingTo("40");
        assertThat(t.getSituacao()).isEqualTo(SituacaoContaPagar.PAGO_PARCIAL);
        assertThat(inversa2.getTipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(inversa2.getValor()).isEqualByComparingTo("65");
        assertThat(inversa2.getOrigem()).isEqualTo(OrigemMovimentacao.ESTORNO_PAGAMENTO);
        assertThat(b2.pagamento().isEstornada()).isTrue();
        assertThat(b2.movimentacao().isEstornada()).isTrue();

        PagamentoCalculo.estornar(b1.pagamento(), b1.movimentacao(), "Pagamento em duplicidade", null, HOJE, LocalDateTime.now());
        assertThat(t.getValorPago()).isEqualByComparingTo("0");
        assertThat(t.getSituacao()).isEqualTo(SituacaoContaPagar.ABERTO);
    }

    @Test
    void pagamentoJaEstornadoNaoEstornaDeNovo() {
        ContaPagar t = titulo("100");
        PagamentoCalculo.ResultadoPagamento b = pagar(t, "40", null, null, null);
        PagamentoCalculo.estornar(b.pagamento(), b.movimentacao(), "Pagamento em duplicidade", null, HOJE, LocalDateTime.now());
        assertThatThrownBy(() -> PagamentoCalculo.estornar(b.pagamento(), b.movimentacao(), "Outra vez aqui", null, HOJE, LocalDateTime.now()))
                .hasMessageContaining("já foi estornado");
    }

    @Test
    void estornoEhPermitidoComBancoInativo() {
        ContaPagar t = titulo("100");
        PagamentoCalculo.ResultadoPagamento b = pagar(t, "40", null, null, null);
        b.movimentacao().getBanco().setAtivo(false);
        assertThat(PagamentoCalculo.estornar(b.pagamento(), b.movimentacao(), "Correção do histórico", null, HOJE, LocalDateTime.now()).getValor())
                .isEqualByComparingTo("40");
    }

    @Test
    void saldoDoBancoAposPagamentosEEstornos() {
        Banco banco = banco(true);
        MovimentacaoBanco inicial = CalculoFinanceiro.movimentacao(banco, HOJE, TipoMovimentacao.ENTRADA, v("500"), OrigemMovimentacao.SALDO_INICIAL, "Saldo", null);
        ContaPagar t = titulo("300");
        PagamentoCalculo.ResultadoPagamento b1 = PagamentoCalculo.pagar(t, banco, forma(true), HOJE, v("200"), v("10"), null, v("5"), null, null, HOJE);
        PagamentoCalculo.ResultadoPagamento b2 = PagamentoCalculo.pagar(t, banco, forma(true), HOJE, v("100"), null, null, null, null, null, HOJE);
        List<MovimentacaoBanco> todas = new ArrayList<>(List.of(inicial, b1.movimentacao(), b2.movimentacao()));
        // 500 - 205 - 100 = 195
        assertThat(CalculoFinanceiro.saldo(todas)).isEqualByComparingTo("195");
        todas.add(PagamentoCalculo.estornar(b1.pagamento(), b1.movimentacao(), "Pagamento em duplicidade", null, HOJE, LocalDateTime.now()));
        // o original estornado continua no extrato e a inversa devolve 205: 195 + 205 = 400
        assertThat(CalculoFinanceiro.saldo(todas)).isEqualByComparingTo("400");
        todas.add(PagamentoCalculo.estornar(b2.pagamento(), b2.movimentacao(), "Pagamento em duplicidade", null, HOJE, LocalDateTime.now()));
        assertThat(CalculoFinanceiro.saldo(todas)).isEqualByComparingTo("500");
    }

    @Test
    void movimentacaoDePagamentoNaoEhEstornavelPelaRotaDeMovimentacoes() {
        ContaPagar t = titulo("100");
        PagamentoCalculo.ResultadoPagamento b = pagar(t, "40", null, null, null);
        assertThatThrownBy(() -> CalculoFinanceiro.validarEstornavel(b.movimentacao())).hasMessageContaining("estorno do pagamento");
    }
}
