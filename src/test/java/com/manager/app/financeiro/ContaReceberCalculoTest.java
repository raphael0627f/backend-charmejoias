package com.manager.app.financeiro;

import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// cálculos e validações do título a receber (parcelamento, competência, situação, regras de edição e cancelamento)
class ContaReceberCalculoTest {

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    @Test
    void parcelasIguaisComCentavoNaUltima() {
        assertThat(ContaReceberCalculo.dividir(v("100.00"), 3)).containsExactly(v("33.33"), v("33.33"), v("33.34"));
        assertThat(ContaReceberCalculo.dividir(v("90"), 3)).containsExactly(v("30.00"), v("30.00"), v("30.00"));
    }

    @Test
    void parcelaMenorQueUmCentavoEhRecusada() {
        assertThatThrownBy(() -> ContaReceberCalculo.dividir(v("0.01"), 2)).isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void limitesDeQuantidadeDeParcelas() {
        ContaReceberCalculo.validarQuantidade(2);
        ContaReceberCalculo.validarQuantidade(120);
        assertThatThrownBy(() -> ContaReceberCalculo.validarQuantidade(1)).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> ContaReceberCalculo.validarQuantidade(121)).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> ContaReceberCalculo.validarQuantidade(null)).isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void valoresPersonalizadosDevemSomarOTotal() {
        assertThat(ContaReceberCalculo.validarValores(List.of(v("10"), v("20.50")), v("30.50"), 2)).containsExactly(v("10.00"), v("20.50"));
        assertThatThrownBy(() -> ContaReceberCalculo.validarValores(List.of(v("10"), v("20")), v("30.50"), 2))
                .isInstanceOf(ItemInvalidoException.class).hasMessage("A soma dos valores das parcelas deve ser igual ao valor total.");
        assertThatThrownBy(() -> ContaReceberCalculo.validarValores(List.of(v("30.50")), v("30.50"), 2)).isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void datasMensalSemAcumularOAjusteDoDia31() {
        assertThat(ContaReceberCalculo.datas(IntervaloParcelamento.MENSAL, d("2026-01-31"), null, 3))
                .containsExactly(d("2026-01-31"), d("2026-02-28"), d("2026-03-31"));
    }

    @Test
    void datasQuinzenalSemanalEPersonalizado() {
        assertThat(ContaReceberCalculo.datas(IntervaloParcelamento.QUINZENAL, d("2026-11-01"), null, 3))
                .containsExactly(d("2026-11-01"), d("2026-11-16"), d("2026-12-01"));
        assertThat(ContaReceberCalculo.datas(IntervaloParcelamento.SEMANAL, d("2026-11-01"), null, 2)).containsExactly(d("2026-11-01"), d("2026-11-08"));
        assertThat(ContaReceberCalculo.datas(IntervaloParcelamento.PERSONALIZADO, null, List.of(d("2026-11-05"), d("2027-01-10")), 2))
                .containsExactly(d("2026-11-05"), d("2027-01-10"));
        assertThatThrownBy(() -> ContaReceberCalculo.datas(IntervaloParcelamento.PERSONALIZADO, null, List.of(d("2026-11-05")), 2))
                .isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> ContaReceberCalculo.datas(IntervaloParcelamento.MENSAL, null, null, 2)).isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void descricaoDaParcelaSubstituiSufixoExistente() {
        assertThat(ContaReceberCalculo.descricaoParcela("Venda de joias", 2, 12)).isEqualTo("Venda de joias 2/12");
        assertThat(ContaReceberCalculo.descricaoParcela("Venda de joias 1/6", 2, 12)).isEqualTo("Venda de joias 2/12");
    }

    @Test
    void competenciaPadraoEhOMesDoVencimentoEValidaFormato() {
        assertThat(ContaReceberCalculo.competenciaPadrao(d("2026-12-10"))).isEqualTo("2026-12");
        assertThat(ContaReceberCalculo.validarCompetencia(" 2026-11 ")).isEqualTo("2026-11");
        assertThatThrownBy(() -> ContaReceberCalculo.validarCompetencia("11/2026")).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> ContaReceberCalculo.validarCompetencia("2026-13")).isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void vencimentoNaoPodeSerAnteriorAEmissao() {
        ContaReceberCalculo.validarVencimento(d("2026-11-16"), d("2026-11-16"));
        ContaReceberCalculo.validarVencimento(d("2026-11-16"), d("2026-12-01"));
        assertThatThrownBy(() -> ContaReceberCalculo.validarVencimento(d("2026-11-16"), d("2026-11-15")))
                .isInstanceOf(ItemInvalidoException.class).hasMessage("O vencimento não pode ser anterior à data de emissão.");
        assertThatThrownBy(() -> ContaReceberCalculo.validarVencimento(d("2026-11-16"), null))
                .isInstanceOf(ItemInvalidoException.class).hasMessage("O vencimento é obrigatório.");
    }

    @Test
    void situacaoVencidaEhCalculadaApenasParaAbertoEParcial() {
        LocalDate hoje = d("2026-11-16");
        assertThat(ContaReceberCalculo.situacaoExibicao(SituacaoContaReceber.ABERTO, d("2026-11-15"), hoje)).isEqualTo(SituacaoExibicaoContaReceber.VENCIDO);
        assertThat(ContaReceberCalculo.situacaoExibicao(SituacaoContaReceber.RECEBIDO_PARCIAL, d("2026-11-15"), hoje)).isEqualTo(SituacaoExibicaoContaReceber.VENCIDO);
        assertThat(ContaReceberCalculo.situacaoExibicao(SituacaoContaReceber.ABERTO, hoje, hoje)).isEqualTo(SituacaoExibicaoContaReceber.ABERTO);
        assertThat(ContaReceberCalculo.situacaoExibicao(SituacaoContaReceber.RECEBIDO, d("2020-01-01"), hoje)).isEqualTo(SituacaoExibicaoContaReceber.RECEBIDO);
        assertThat(ContaReceberCalculo.situacaoExibicao(SituacaoContaReceber.CANCELADO, d("2020-01-01"), hoje)).isEqualTo(SituacaoExibicaoContaReceber.CANCELADO);
    }

    @Test
    void situacaoGravadaEValorAberto() {
        assertThat(ContaReceberCalculo.situacaoPara(v("100"), v("0"))).isEqualTo(SituacaoContaReceber.ABERTO);
        assertThat(ContaReceberCalculo.situacaoPara(v("100"), v("40"))).isEqualTo(SituacaoContaReceber.RECEBIDO_PARCIAL);
        assertThat(ContaReceberCalculo.situacaoPara(v("100"), v("100"))).isEqualTo(SituacaoContaReceber.RECEBIDO);
        assertThat(ContaReceberCalculo.valorAberto(SituacaoContaReceber.RECEBIDO_PARCIAL, v("100"), v("40"))).isEqualByComparingTo("60");
        assertThat(ContaReceberCalculo.valorAberto(SituacaoContaReceber.CANCELADO, v("100"), v("0"))).isEqualByComparingTo("0");
        assertThat(ContaReceberCalculo.valorAberto(SituacaoContaReceber.RECEBIDO, v("100"), v("100"))).isEqualByComparingTo("0");
    }

    @Test
    void regrasDeEdicaoComRecebimento() {
        ContaReceberCalculo.validarValorNaoAbaixoDoRecebido(v("40"), v("40"));
        assertThatThrownBy(() -> ContaReceberCalculo.validarValorNaoAbaixoDoRecebido(v("39.99"), v("40")))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("inferior ao valor já recebido");
        ContaReceberCalculo.validarTrocaDeCliente(true, v("0"));
        ContaReceberCalculo.validarTrocaDeCliente(false, v("40"));
        assertThatThrownBy(() -> ContaReceberCalculo.validarTrocaDeCliente(true, v("0.01"))).isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void cancelamentoEVencimentoSegundoASituacao() {
        ContaReceberCalculo.validarCancelavel(SituacaoContaReceber.ABERTO, v("0"));
        assertThatThrownBy(() -> ContaReceberCalculo.validarCancelavel(SituacaoContaReceber.CANCELADO, v("0")))
                .isInstanceOf(ItemInvalidoException.class).hasMessage("O título já está cancelado.");
        assertThatThrownBy(() -> ContaReceberCalculo.validarCancelavel(SituacaoContaReceber.RECEBIDO_PARCIAL, v("10")))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("recebimentos ativos");
        ContaReceberCalculo.validarVencimentoAlteravel(SituacaoContaReceber.ABERTO);
        ContaReceberCalculo.validarVencimentoAlteravel(SituacaoContaReceber.RECEBIDO_PARCIAL);
        assertThatThrownBy(() -> ContaReceberCalculo.validarVencimentoAlteravel(SituacaoContaReceber.RECEBIDO)).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> ContaReceberCalculo.validarVencimentoAlteravel(SituacaoContaReceber.CANCELADO)).isInstanceOf(ItemInvalidoException.class);
    }
}
