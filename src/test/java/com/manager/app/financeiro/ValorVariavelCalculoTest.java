package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.ContaPagarRequestDTO;
import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// recorrência de valor e vencimento variáveis: confirmação no pagamento, obrigatoriedade do valor da conta, totais estimados e herança do indicador
class ValorVariavelCalculoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 14);

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    private static ContaPagar titulo(boolean estimado, String valor, String vencimento) {
        ContaPagar c = new ContaPagar();
        c.setId(10L);
        c.setNumero(7L);
        c.setDescricao("Conta de água");
        c.setValor(v(valor));
        c.setValorPago(BigDecimal.ZERO);
        c.setSituacao(SituacaoContaPagar.ABERTO);
        c.setVencimento(d(vencimento));
        c.setEstimado(estimado);
        return c;
    }

    private static Banco banco() {
        Banco b = new Banco();
        b.setId(1L);
        b.setNome("Caixa");
        b.setAtivo(true);
        return b;
    }

    private static FormaPagamento forma() {
        FormaPagamento f = new FormaPagamento();
        f.setId(1L);
        f.setNome("PIX");
        f.setAtivo(true);
        return f;
    }

    private static void pagar(ContaPagar t, String principal) {
        PagamentoCalculo.pagar(t, banco(), forma(), HOJE, v(principal), null, null, null, null, null, HOJE);
    }

    // ---------- quando a confirmação é permitida ----------

    @Test
    void tituloEstimadoSemPagamentoAceitaConfirmacaoEAtualizaValorVencimentoEIndicador() {
        ContaPagar t = titulo(true, "180.00", "2026-12-10");
        PagamentoCalculo.Confirmacao c = PagamentoCalculo.confirmar(t, v("212.35"), d("2026-12-12"), false);
        assertThat(c).isNotNull();
        assertThat(t.getValor()).isEqualByComparingTo("212.35");
        assertThat(t.getVencimento()).isEqualTo(d("2026-12-12"));
        assertThat(t.isEstimado()).isFalse();
        assertThat(c.descricao()).isEqualTo("Dados da conta confirmados: valor de R$ 180,00 para R$ 212,35; vencimento de 10/12/2026 para 12/12/2026.");
    }

    @Test
    void vencimentoOmitidoMantemOPrevisto() {
        ContaPagar t = titulo(true, "180.00", "2026-12-10");
        PagamentoCalculo.confirmar(t, v("150.00"), null, false);
        assertThat(t.getVencimento()).isEqualTo(d("2026-12-10"));
        assertThat(t.isEstimado()).isFalse();
    }

    @Test
    void vencimentoRealPodeSerAnteriorOuPosteriorAoPrevisto() {
        ContaPagar a = titulo(true, "100", "2026-12-10");
        PagamentoCalculo.confirmar(a, v("100"), d("2026-12-01"), false);
        assertThat(a.getVencimento()).isEqualTo(d("2026-12-01"));
        ContaPagar b = titulo(true, "100", "2026-12-10");
        PagamentoCalculo.confirmar(b, v("100"), d("2026-12-28"), false);
        assertThat(b.getVencimento()).isEqualTo(d("2026-12-28"));
    }

    @Test
    void tituloComumRecusaValorContaOuVencimento() {
        assertThatThrownBy(() -> PagamentoCalculo.confirmar(titulo(false, "100", "2026-12-10"), v("90"), null, false))
                .isInstanceOf(ItemInvalidoException.class).hasMessage(PagamentoCalculo.MSG_CONFIRMACAO_INDEVIDA);
        assertThatThrownBy(() -> PagamentoCalculo.confirmar(titulo(false, "100", "2026-12-10"), null, d("2026-12-11"), false))
                .isInstanceOf(ItemInvalidoException.class).hasMessage(PagamentoCalculo.MSG_CONFIRMACAO_INDEVIDA);
    }

    @Test
    void tituloComumSemOsCamposNaoTemNadaAConfirmar() {
        assertThat(PagamentoCalculo.confirmar(titulo(false, "100", "2026-12-10"), null, null, false)).isNull();
    }

    @Test
    void comPagamentoAtivoOsCamposSaoRecusados() {
        assertThatThrownBy(() -> PagamentoCalculo.confirmar(titulo(true, "100", "2026-12-10"), v("90"), null, true))
                .isInstanceOf(ItemInvalidoException.class).hasMessage(PagamentoCalculo.MSG_CONFIRMACAO_INDEVIDA);
    }

    @Test
    void tituloCanceladoNaoPodeSerConfirmado() {
        ContaPagar t = titulo(true, "100", "2026-12-10");
        t.setSituacao(SituacaoContaPagar.CANCELADO);
        assertThatThrownBy(() -> PagamentoCalculo.confirmar(t, v("90"), null, false)).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("cancelado");
    }

    // ---------- obrigatoriedade do valor da conta ----------

    @Test
    void tituloEstimadoExigeValorDaConta() {
        assertThatThrownBy(() -> PagamentoCalculo.confirmar(titulo(true, "100", "2026-12-10"), null, null, false))
                .isInstanceOf(ItemInvalidoException.class).hasMessage("Informe o valor da conta deste mês para registrar o pagamento.");
        assertThatThrownBy(() -> PagamentoCalculo.confirmar(titulo(true, "100", "2026-12-10"), null, d("2026-12-12"), false))
                .isInstanceOf(ItemInvalidoException.class).hasMessage(PagamentoCalculo.MSG_VALOR_CONTA_OBRIGATORIO);
    }

    @Test
    void valorDaContaDeveSerPositivoComDuasCasasEAbaixoDoTeto() {
        assertThatThrownBy(() -> PagamentoCalculo.confirmar(titulo(true, "100", "2026-12-10"), v("0"), null, false)).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> PagamentoCalculo.confirmar(titulo(true, "100", "2026-12-10"), v("-5"), null, false)).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> PagamentoCalculo.confirmar(titulo(true, "100", "2026-12-10"), v("10.123"), null, false)).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> PagamentoCalculo.confirmar(titulo(true, "100", "2026-12-10"), v("10000000.00"), null, false))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("9.999.999,99");
    }

    // ---------- valor em aberto atualizado e pagamento sobre o valor novo ----------

    @Test
    void pagamentoUsaOValorNovoParaOValorEmAbertoENaoOEstimado() {
        ContaPagar maior = titulo(true, "180.00", "2026-12-10");
        PagamentoCalculo.confirmar(maior, v("250.00"), null, false);
        pagar(maior, "250.00");
        assertThat(maior.getSituacao()).isEqualTo(SituacaoContaPagar.PAGO);

        ContaPagar menor = titulo(true, "180.00", "2026-12-10");
        PagamentoCalculo.confirmar(menor, v("120.00"), null, false);
        assertThatThrownBy(() -> pagar(menor, "180.00")).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("R$ 120,00");
        pagar(menor, "120.00");
        assertThat(menor.getSituacao()).isEqualTo(SituacaoContaPagar.PAGO);

        ContaPagar igual = titulo(true, "180.00", "2026-12-10");
        PagamentoCalculo.confirmar(igual, v("180.00"), null, false);
        pagar(igual, "180.00");
        assertThat(igual.getSituacao()).isEqualTo(SituacaoContaPagar.PAGO);
    }

    @Test
    void principalMenorQueOValorConfirmadoDeixaOTituloParcial() {
        ContaPagar t = titulo(true, "180.00", "2026-12-10");
        PagamentoCalculo.confirmar(t, v("200.00"), null, false);
        pagar(t, "50.00");
        assertThat(t.getSituacao()).isEqualTo(SituacaoContaPagar.PAGO_PARCIAL);
        assertThat(ContaPagarCalculo.valorAberto(t.getSituacao(), t.getValor(), t.getValorPago())).isEqualByComparingTo("150.00");
        assertThat(t.isEstimado()).isFalse();
    }

    // ---------- pagamento em lote ----------

    @Test
    void loteRecusaTituloEstimadoEAceitaOsDemais() {
        assertThatThrownBy(() -> PagamentoCalculo.saldoIntegral(titulo(true, "100", "2026-12-10")))
                .isInstanceOf(ItemInvalidoException.class).hasMessage("Título de valor variável: confirme o valor da conta no pagamento individual.");
        assertThat(PagamentoCalculo.saldoIntegral(titulo(false, "100", "2026-12-10"))).isEqualByComparingTo("100");
    }

    // ---------- totais do estimado ----------

    @Test
    void valorAbertoEstimadoSomenteDeAbertoOuParcialEstimado() {
        assertThat(ContaPagarCalculo.valorAbertoEstimado(SituacaoContaPagar.ABERTO, true, v("180"), v("0"))).isEqualByComparingTo("180");
        assertThat(ContaPagarCalculo.valorAbertoEstimado(SituacaoContaPagar.PAGO_PARCIAL, true, v("180"), v("30"))).isEqualByComparingTo("150");
        assertThat(ContaPagarCalculo.valorAbertoEstimado(SituacaoContaPagar.ABERTO, false, v("180"), v("0"))).isEqualByComparingTo("0");
        assertThat(ContaPagarCalculo.valorAbertoEstimado(SituacaoContaPagar.CANCELADO, true, v("180"), v("0"))).isEqualByComparingTo("0");
        assertThat(ContaPagarCalculo.valorAbertoEstimado(SituacaoContaPagar.PAGO, true, v("180"), v("180"))).isEqualByComparingTo("0");
    }

    @Test
    void quantidadeDeEstimadosIgnoraCanceladosENaoEstimados() {
        assertThat(ContaPagarCalculo.contaComoEstimado(SituacaoContaPagar.ABERTO, true)).isTrue();
        assertThat(ContaPagarCalculo.contaComoEstimado(SituacaoContaPagar.CANCELADO, true)).isFalse();
        assertThat(ContaPagarCalculo.contaComoEstimado(SituacaoContaPagar.ABERTO, false)).isFalse();
    }

    // ---------- herança do indicador na geração (criação e rotina diária usam a mesma montagem) ----------

    private static ContaPagarRecorrencia regra(boolean variavel) {
        ContaPagarRecorrencia r = new ContaPagarRecorrencia();
        r.setId(5L);
        r.setPeriodicidade(PeriodicidadeRecorrencia.MENSAL);
        r.setDataInicio(d("2026-12-10"));
        r.setDescricao("Conta de água");
        r.setValor(v("180.00"));
        r.setValorVariavel(variavel);
        return r;
    }

    @Test
    void ocorrenciasDeRegraVariavelNascemEstimadasEAsComunsNao() {
        ContaPagar a = RecorrenciaCalculo.montarOcorrencia(regra(true), d("2027-01-10"), HOJE);
        assertThat(a.isEstimado()).isTrue();
        assertThat(a.getValor()).isEqualByComparingTo("180.00");
        assertThat(a.getVencimento()).isEqualTo(d("2027-01-10"));
        assertThat(a.getOcorrenciaData()).isEqualTo(d("2027-01-10"));
        assertThat(a.getSituacao()).isEqualTo(SituacaoContaPagar.ABERTO);
        assertThat(RecorrenciaCalculo.montarOcorrencia(regra(false), d("2027-01-10"), HOJE).isEstimado()).isFalse();
    }

    @Test
    void rotinaDiariaHerdaOIndicadorEGeraSoAsFaltantes() {
        ContaPagarRecorrencia regra = regra(true);
        List<LocalDate> faltantes = RecorrenciaCalculo.faltantes(PeriodicidadeRecorrencia.MENSAL, regra.getDataInicio(), d("2027-03-10"),
                List.of(d("2026-12-10"), d("2027-01-10")));
        assertThat(faltantes).containsExactly(d("2027-02-10"), d("2027-03-10"));
        faltantes.forEach(f -> assertThat(RecorrenciaCalculo.montarOcorrencia(regra, f, HOJE).isEstimado()).isTrue());
        assertThat(RecorrenciaCalculo.faltantes(PeriodicidadeRecorrencia.MENSAL, regra.getDataInicio(), d("2027-03-10"),
                List.of(d("2026-12-10"), d("2027-01-10"), d("2027-02-10"), d("2027-03-10")))).isEmpty();
    }

    // ---------- indicador no pedido de criação ----------

    private static ContaPagarRequestDTO pedido(Boolean raiz, ContaPagarRequestDTO.Recorrencia rec) {
        return new ContaPagarRequestDTO("Água", null, 1L, null, v("180"), null, null, d("2026-12-10"), null, null, null, null, null, rec, raiz, null, null);
    }

    @Test
    void valorVariavelValeNoBlocoOuNaRaizSomenteComRecorrencia() {
        var mensal = new ContaPagarRequestDTO.Recorrencia(PeriodicidadeRecorrencia.MENSAL, null, true);
        assertThat(RecorrenciaCalculo.valorVariavel(pedido(null, mensal))).isTrue();
        assertThat(RecorrenciaCalculo.valorVariavel(pedido(true, new ContaPagarRequestDTO.Recorrencia(PeriodicidadeRecorrencia.MENSAL, null, null)))).isTrue();
        assertThat(RecorrenciaCalculo.valorVariavel(pedido(null, new ContaPagarRequestDTO.Recorrencia(PeriodicidadeRecorrencia.MENSAL, null, null)))).isFalse();
        assertThat(RecorrenciaCalculo.valorVariavel(pedido(false, null))).isFalse();
        assertThatThrownBy(() -> RecorrenciaCalculo.valorVariavel(pedido(true, null))).isInstanceOf(ItemInvalidoException.class)
                .hasMessage("O indicador de valor variável só pode ser usado em lançamento recorrente.");
    }

    // ---------- reajuste ----------

    @Test
    void reajusteDeRegraVariavelNaoAlteraOcorrenciaJaConfirmada() {
        ContaPagar estimada = titulo(true, "180", "2027-02-10");
        ContaPagar confirmada = titulo(false, "212.35", "2027-02-10");
        assertThat(RecorrenciaCalculo.reajustavel(estimada, d("2027-01-01"), false, true)).isTrue();
        assertThat(RecorrenciaCalculo.reajustavel(confirmada, d("2027-01-01"), false, true)).isFalse();
        assertThat(RecorrenciaCalculo.reajustavel(confirmada, d("2027-01-01"), false, false)).isTrue();
        assertThat(RecorrenciaCalculo.reajustavel(estimada, d("2027-01-01"), true, true)).isFalse();
    }
}
