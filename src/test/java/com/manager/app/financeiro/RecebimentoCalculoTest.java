package com.manager.app.financeiro;

import com.manager.app.cliente.Cliente;
import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// cálculos e validações do recebimento e do estorno de recebimento
class RecebimentoCalculoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 16);

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

    private static ContaReceber titulo(String valor) {
        Cliente cliente = new Cliente();
        cliente.setNome("Maria");
        ContaReceber c = new ContaReceber();
        c.setId(10L);
        c.setNumero(7L);
        c.setCliente(cliente);
        c.setDescricao("Venda de joias");
        c.setValor(v(valor));
        c.setValorRecebido(BigDecimal.ZERO);
        c.setSituacao(SituacaoContaReceber.ABERTO);
        return c;
    }

    private static RecebimentoCalculo.ResultadoRecebimento receber(ContaReceber t, String principal, String juros, String multa, String desconto) {
        return RecebimentoCalculo.receber(t, banco(true), forma(true), HOJE, v(principal), juros == null ? null : v(juros),
                multa == null ? null : v(multa), desconto == null ? null : v(desconto), null, null, HOJE);
    }

    @Test
    void valorTotalSomaJurosMultaEDescontaDesconto() {
        assertThat(RecebimentoCalculo.valorTotal(v("1000"), v("12.50"), v("20"), v("5"))).isEqualByComparingTo("1027.50");
    }

    @Test
    void recebimentoParcialAbateSoOPrincipalEGeraEntrada() {
        ContaReceber t = titulo("1000");
        RecebimentoCalculo.ResultadoRecebimento r = receber(t, "400", "10", "5", "0");
        assertThat(t.getValorRecebido()).isEqualByComparingTo("400");
        assertThat(t.getSituacao()).isEqualTo(SituacaoContaReceber.RECEBIDO_PARCIAL);
        assertThat(r.recebimento().getValorTotal()).isEqualByComparingTo("415");
        assertThat(r.movimentacao().getTipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(r.movimentacao().getValor()).isEqualByComparingTo("415");
        assertThat(r.movimentacao().getOrigem()).isEqualTo(OrigemMovimentacao.RECEBIMENTO_CONTA_RECEBER);
        assertThat(r.movimentacao().getDescricao()).isEqualTo("Recebimento do título nº 7 - Venda de joias - Maria");
        assertThat(r.movimentacao().getContaReceberId()).isEqualTo(10L);
        assertThat(ContaReceberCalculo.valorAberto(t.getSituacao(), t.getValor(), t.getValorRecebido())).isEqualByComparingTo("600");
    }

    @Test
    void recebimentoDoSaldoQuitaOTituloEJurosNaoAlteramOValor() {
        ContaReceber t = titulo("1000");
        receber(t, "1000", "50", null, null);
        assertThat(t.getSituacao()).isEqualTo(SituacaoContaReceber.RECEBIDO);
        assertThat(t.getValorRecebido()).isEqualByComparingTo("1000");
        assertThat(t.getValor()).isEqualByComparingTo("1000");
    }

    @Test
    void recebimentoDivididoEmDoisAteQuitar() {
        ContaReceber t = titulo("100");
        receber(t, "60", null, null, null);
        assertThat(t.getSituacao()).isEqualTo(SituacaoContaReceber.RECEBIDO_PARCIAL);
        receber(t, "40", null, null, null);
        assertThat(t.getSituacao()).isEqualTo(SituacaoContaReceber.RECEBIDO);
    }

    @Test
    void descontoReduzOTotalMasNaoOAbatimento() {
        ContaReceber t = titulo("100");
        RecebimentoCalculo.ResultadoRecebimento r = receber(t, "100", null, null, "10");
        assertThat(r.recebimento().getValorTotal()).isEqualByComparingTo("90");
        assertThat(t.getValorRecebido()).isEqualByComparingTo("100");
        assertThat(t.getSituacao()).isEqualTo(SituacaoContaReceber.RECEBIDO);
    }

    @Test
    void recusaPrincipalAcimaDoAbertoDescontoAcimaDoLimiteETotalZero() {
        ContaReceber t = titulo("100");
        assertThatThrownBy(() -> receber(t, "100.01", null, null, null)).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("em aberto");
        assertThatThrownBy(() -> receber(t, "50", "5", "5", "60.01")).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("desconto");
        assertThatThrownBy(() -> receber(t, "50", null, null, "50")).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("maior que zero");
        assertThatThrownBy(() -> receber(t, "0", null, null, null)).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> receber(t, "10", "-1", null, null)).isInstanceOf(ItemInvalidoException.class);
        assertThat(t.getValorRecebido()).isEqualByComparingTo("0");
    }

    @Test
    void recusaDataFuturaBancoEFormaDesativados() {
        ContaReceber t = titulo("100");
        assertThatThrownBy(() -> RecebimentoCalculo.receber(t, banco(true), forma(true), HOJE.plusDays(1), v("10"), null, null, null, null, null, HOJE))
                .isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> RecebimentoCalculo.receber(t, banco(false), forma(true), HOJE, v("10"), null, null, null, null, null, HOJE))
                .isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> RecebimentoCalculo.receber(t, banco(true), forma(false), HOJE, v("10"), null, null, null, null, null, HOJE))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("desativada");
    }

    @Test
    void recusaTituloCanceladoERecebido() {
        ContaReceber c = titulo("100");
        c.setSituacao(SituacaoContaReceber.CANCELADO);
        assertThatThrownBy(() -> receber(c, "10", null, null, null)).isInstanceOf(ItemInvalidoException.class).hasMessage("O título cancelado não admite recebimento.");
        ContaReceber r = titulo("100");
        r.setSituacao(SituacaoContaReceber.RECEBIDO);
        assertThatThrownBy(() -> receber(r, "10", null, null, null)).isInstanceOf(ItemInvalidoException.class).hasMessage("O título já está integralmente recebido.");
    }

    @Test
    void saldoIntegralParaOLote() {
        ContaReceber t = titulo("100");
        receber(t, "30", null, null, null);
        assertThat(RecebimentoCalculo.saldoIntegral(t)).isEqualByComparingTo("70");
        t.setSituacao(SituacaoContaReceber.CANCELADO);
        assertThatThrownBy(() -> RecebimentoCalculo.saldoIntegral(t)).isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void estornoParcialETotalRecalculamValorESituacao() {
        ContaReceber t = titulo("100");
        RecebimentoCalculo.ResultadoRecebimento r1 = receber(t, "60", "5", null, null);
        RecebimentoCalculo.ResultadoRecebimento r2 = receber(t, "40", null, null, null);
        r1.recebimento().setId(1L);
        r2.recebimento().setId(2L);
        assertThat(t.getSituacao()).isEqualTo(SituacaoContaReceber.RECEBIDO);

        MovimentacaoBanco inv = RecebimentoCalculo.estornar(r2.recebimento(), r2.movimentacao(), "Cheque devolvido", null, HOJE, LocalDateTime.now());
        assertThat(t.getValorRecebido()).isEqualByComparingTo("60");
        assertThat(t.getSituacao()).isEqualTo(SituacaoContaReceber.RECEBIDO_PARCIAL);
        assertThat(r2.recebimento().isEstornada()).isTrue();
        assertThat(r2.movimentacao().isEstornada()).isTrue();
        assertThat(inv.getTipo()).isEqualTo(TipoMovimentacao.SAIDA);
        assertThat(inv.getValor()).isEqualByComparingTo("40");
        assertThat(inv.getOrigem()).isEqualTo(OrigemMovimentacao.ESTORNO_RECEBIMENTO);
        assertThat(inv.getEstornoDe()).isSameAs(r2.movimentacao());
        assertThat(inv.getRecebimentoId()).isEqualTo(2L);

        MovimentacaoBanco inv1 = RecebimentoCalculo.estornar(r1.recebimento(), r1.movimentacao(), "Estorno total", null, HOJE, LocalDateTime.now());
        assertThat(t.getValorRecebido()).isEqualByComparingTo("0");
        assertThat(t.getSituacao()).isEqualTo(SituacaoContaReceber.ABERTO);
        assertThat(inv1.getValor()).isEqualByComparingTo("65");
    }

    @Test
    void estornoRepetidoEhRecusado() {
        ContaReceber t = titulo("100");
        RecebimentoCalculo.ResultadoRecebimento r = receber(t, "100", null, null, null);
        RecebimentoCalculo.estornar(r.recebimento(), r.movimentacao(), "Primeiro estorno", null, HOJE, LocalDateTime.now());
        assertThatThrownBy(() -> RecebimentoCalculo.estornar(r.recebimento(), r.movimentacao(), "Segundo estorno", null, HOJE, LocalDateTime.now()))
                .isInstanceOf(ItemInvalidoException.class).hasMessage("Este recebimento já foi estornado.");
        assertThat(t.getValorRecebido()).isEqualByComparingTo("0");
    }

    @Test
    void movimentacoesDeRecebimentoNaoSaoEstornaveisPelaTelaDeMovimentacoes() {
        MovimentacaoBanco m = new MovimentacaoBanco();
        m.setOrigem(OrigemMovimentacao.RECEBIMENTO_CONTA_RECEBER);
        assertThatThrownBy(() -> CalculoFinanceiro.validarEstornavel(m)).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("recebimento");
        m.setOrigem(OrigemMovimentacao.ESTORNO_RECEBIMENTO);
        assertThatThrownBy(() -> CalculoFinanceiro.validarEstornavel(m)).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("recebimento");
    }
}
