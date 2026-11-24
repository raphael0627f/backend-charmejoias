package com.manager.app.financeiro;

import com.manager.app.financeiro.ClienteFinanceiroCalculo.Filtro;
import com.manager.app.financeiro.ClienteFinanceiroCalculo.Selo;
import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValorRealmentePagoTest {

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    private static ContaReceberRecebimento rec(String principal, String juros, String multa, String desconto, String total, String taxa,
                                               ContaReceberRecebimento.Origem origem, boolean estornada) {
        ContaReceberRecebimento r = new ContaReceberRecebimento();
        r.setValorPrincipal(v(principal));
        r.setJuros(v(juros));
        r.setMulta(v(multa));
        r.setDesconto(v(desconto));
        r.setValorTotal(v(total));
        r.setValorTaxaCliente(v(taxa));
        r.setOrigem(origem);
        r.setEstornada(estornada);
        return r;
    }

    @Test
    void totalRealSomaPrincipalJurosMultaMenosDescontoSemEstornados() {
        var t = TotaisRecebimento.de(1L, List.of(
                rec("100.00", "5.00", "2.00", "1.00", "106.00", "0.00", ContaReceberRecebimento.Origem.BANCO, false),
                rec("50.00", "0.00", "0.00", "0.00", "50.00", "0.00", ContaReceberRecebimento.Origem.BANCO, true)));
        assertThat(t.total()).isEqualByComparingTo("106.00");
        assertThat(t.principal()).isEqualByComparingTo("100.00");
        assertThat(t.juros()).isEqualByComparingTo("5.00");
        assertThat(t.multa()).isEqualByComparingTo("2.00");
        assertThat(t.desconto()).isEqualByComparingTo("1.00");
        assertThat(t.creditoAplicado()).isFalse();
    }

    @Test
    void creditoDoClienteContaNoTotalEFicaIndicado() {
        var t = TotaisRecebimento.de(1L, List.of(
                rec("30.00", "0.00", "0.00", "0.00", "30.00", "0.00", ContaReceberRecebimento.Origem.CREDITO_CLIENTE, false),
                rec("70.00", "0.00", "0.00", "0.00", "70.00", "0.00", ContaReceberRecebimento.Origem.BANCO, false)));
        assertThat(t.total()).isEqualByComparingTo("100.00");
        assertThat(t.credito()).isEqualByComparingTo("30.00");
        assertThat(t.creditoAplicado()).isTrue();
    }

    @Test
    void taxaDoClienteInfinitePayFicaForaDoTotal() {
        var t = TotaisRecebimento.de(1L, List.of(
                rec("100.00", "0.00", "0.00", "0.00", "100.00", "2.50", ContaReceberRecebimento.Origem.INFINITEPAY, false)));
        assertThat(t.total()).isEqualByComparingTo("100.00");
        assertThat(t.taxaCliente()).isEqualByComparingTo("2.50");
    }

    @Test
    void semRecebimentoOuTituloAusenteDoMapaDaZero() {
        assertThat(TotaisRecebimento.de(1L, List.of()).total()).isEqualByComparingTo("0");
        assertThat(TotaisRecebimento.de(Map.of(), 9L)).isSameAs(TotaisRecebimento.ZERO);
    }

    @Test
    void seloSegueAPrecedenciaDefinida() {
        BigDecimal zero = BigDecimal.ZERO;
        assertThat(ClienteFinanceiroCalculo.selo(true, 2, 2, true, v("10"), v("10"), zero, false, false)).isEqualTo(Selo.CANCELADA);
        assertThat(ClienteFinanceiroCalculo.selo(false, 2, 0, true, v("10"), zero, zero, false, false)).isEqualTo(Selo.CANCELADA);
        assertThat(ClienteFinanceiroCalculo.selo(false, 0, 0, true, zero, zero, zero, false, false)).isEqualTo(Selo.SEM_VALOR_A_PAGAR);
        assertThat(ClienteFinanceiroCalculo.selo(false, 0, 0, true, v("10"), zero, zero, false, false)).isEqualTo(Selo.SEM_TITULO);
        // documento sem valor a pagar mas com saldo em aberto (título de outro documento): vale a situação real dos títulos
        assertThat(ClienteFinanceiroCalculo.selo(false, 2, 2, true, zero, v("34.96"), v("15"), true, true)).isEqualTo(Selo.PARCIAL_VENCIDA);
        assertThat(ClienteFinanceiroCalculo.selo(false, 1, 1, true, zero, zero, v("15"), false, true)).isEqualTo(Selo.QUITADA);
        assertThat(ClienteFinanceiroCalculo.selo(false, 1, 1, true, zero, zero, zero, false, false)).isEqualTo(Selo.SEM_VALOR_A_PAGAR);
        assertThat(ClienteFinanceiroCalculo.selo(false, 1, 1, true, v("10"), zero, v("10"), false, true)).isEqualTo(Selo.QUITADA);
        assertThat(ClienteFinanceiroCalculo.selo(false, 1, 1, true, v("10"), v("10"), zero, true, false)).isEqualTo(Selo.VENCIDA);
        assertThat(ClienteFinanceiroCalculo.selo(false, 1, 1, true, v("10"), v("4"), v("6"), true, true)).isEqualTo(Selo.PARCIAL_VENCIDA);
        assertThat(ClienteFinanceiroCalculo.selo(false, 1, 1, true, v("10"), v("4"), v("6"), false, true)).isEqualTo(Selo.PARCIAL);
        assertThat(ClienteFinanceiroCalculo.selo(false, 1, 1, true, v("10"), v("10"), zero, false, false)).isEqualTo(Selo.EM_ABERTO);
    }

    @Test
    void tituloQuitadoAoLadoDeOutroVencidoNaoEhPagamentoParcial() {
        // pago 20 em um título quitado, outro título vencido em aberto sem recebimento parcial => VENCIDA
        assertThat(ClienteFinanceiroCalculo.selo(false, 2, 2, false, null, v("50"), v("20"), true, false)).isEqualTo(Selo.VENCIDA);
        assertThat(ClienteFinanceiroCalculo.selo(false, 2, 2, false, null, v("50"), v("20"), false, false)).isEqualTo(Selo.EM_ABERTO);
        assertThat(ClienteFinanceiroCalculo.selo(false, 2, 2, false, null, v("50"), v("20"), true, true)).isEqualTo(Selo.PARCIAL_VENCIDA);
    }

    @Test
    void filtrosRapidosAgrupamOsSelos() {
        assertThat(Filtro.EM_ABERTO.aceita(Selo.PARCIAL_VENCIDA)).isTrue();
        assertThat(Filtro.EM_ABERTO.aceita(Selo.QUITADA)).isFalse();
        assertThat(Filtro.VENCIDAS.aceita(Selo.VENCIDA)).isTrue();
        assertThat(Filtro.VENCIDAS.aceita(Selo.PARCIAL)).isFalse();
        assertThat(Filtro.QUITADAS.aceita(Selo.QUITADA)).isTrue();
        assertThat(Filtro.TODAS.aceita(Selo.CANCELADA)).isTrue();
        assertThat(ClienteFinanceiroCalculo.filtro(null)).isEqualTo(Filtro.TODAS);
        assertThat(ClienteFinanceiroCalculo.filtro("vencidas")).isEqualTo(Filtro.VENCIDAS);
        assertThatThrownBy(() -> ClienteFinanceiroCalculo.filtro("xyz")).isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void paginacaoTemPadrao20EMaximo100() {
        assertThat(ClienteFinanceiroCalculo.tamanho(0)).isEqualTo(20);
        assertThat(ClienteFinanceiroCalculo.tamanho(50)).isEqualTo(50);
        assertThat(ClienteFinanceiroCalculo.tamanho(5000)).isEqualTo(100);
        assertThat(ClienteFinanceiroCalculo.pagina(-3)).isEqualTo(0);
    }

    @Test
    void valoresDoDocumentoNaoFicamNegativos() {
        var v = DocumentoValoresService.Valores.de(v("100.00"), v("20.00"));
        assertThat(v.valorAPagar()).isEqualByComparingTo("80.00");
        var neg = DocumentoValoresService.Valores.de(v("10.00"), v("20.00"));
        assertThat(neg.valorAPagar()).isEqualByComparingTo("0.00");
        assertThat(DocumentoValoresService.Valores.de(null, null).valorVendido()).isEqualByComparingTo("0.00");
    }

    @Test
    void totalDaConsignacaoIgnoraCanceladosNosValores() {
        ContaReceber ativo = titulo(1L, "100.00", "40.00", SituacaoContaReceber.RECEBIDO_PARCIAL);
        ContaReceber cancelado = titulo(2L, "50.00", "0.00", SituacaoContaReceber.CANCELADO);
        var pagos = Map.of(1L, new TotaisRecebimento(1L, v("42.00"), v("40.00"), v("2.00"), v("0"), v("0"), v("0"), v("0")));
        var total = DocumentoFinanceiroService.totalConsignacao(List.of(ativo, cancelado), pagos, Map.of());
        assertThat(total.quantidade()).isEqualTo(2);
        assertThat(total.quantidadeCancelados()).isEqualTo(1);
        assertThat(total.valorTitulado()).isEqualByComparingTo("100.00");
        assertThat(total.valorPago()).isEqualByComparingTo("42.00");
        assertThat(total.valorRecebido()).isEqualByComparingTo("40.00");
        assertThat(total.emAberto()).isEqualByComparingTo("60.00");
    }

    private static ContaReceber titulo(Long id, String valor, String recebido, SituacaoContaReceber situacao) {
        ContaReceber c = new ContaReceber();
        c.setId(id);
        c.setValor(v(valor));
        c.setValorRecebido(v(recebido));
        c.setSituacao(situacao);
        c.setVencimento(LocalDate.now().plusDays(5));
        return c;
    }
}
