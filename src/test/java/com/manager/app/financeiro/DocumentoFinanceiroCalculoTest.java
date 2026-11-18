package com.manager.app.financeiro;

import com.manager.app.financeiro.DocumentoFinanceiroCalculo.Saldo;
import com.manager.app.financeiro.DocumentoFinanceiroCalculo.Situacao;
import com.manager.app.financeiro.DocumentoFinanceiroCalculo.Tipo;
import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// valor, saldo e regras de geração do título de pré-acerto/acerto de consignação
class DocumentoFinanceiroCalculoTest {

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    private static Situacao situacao(Tipo tipo, boolean consignacao, boolean confirmado, boolean clienteAtivo, String valor, boolean proprio,
                                     boolean doAcerto, String titulado) {
        BigDecimal vd = v(valor);
        return new Situacao(tipo, consignacao, confirmado, clienteAtivo, "Maria", vd, proprio, doAcerto, DocumentoFinanceiroCalculo.saldo(vd, v(titulado)));
    }

    @Test
    void valorDoPreAcertoEhSomaMenosDescontoAbsoluto() {
        assertThat(DocumentoFinanceiroCalculo.valorPreAcerto(v("1000"), v("150.50"))).isEqualByComparingTo("849.50");
        assertThat(DocumentoFinanceiroCalculo.valorPreAcerto(v("1000"), null)).isEqualByComparingTo("1000");
        assertThat(DocumentoFinanceiroCalculo.valorPreAcerto(null, null)).isEqualByComparingTo("0");
    }

    @Test
    void saldoComTituloDoPreAcerto() {
        Saldo s = DocumentoFinanceiroCalculo.saldo(v("1000"), v("400"));
        assertThat(s.saldoATitular()).isEqualByComparingTo("600");
        assertThat(s.excedente()).isEqualByComparingTo("0");
    }

    @Test
    void semTituloOAcertoCobreOTotal() {
        assertThat(DocumentoFinanceiroCalculo.saldo(v("1000"), v("0")).saldoATitular()).isEqualByComparingTo("1000");
    }

    @Test
    void tituloCanceladoNaoContaPoisNaoEntraNoTitulado() {
        // o cancelado é removido da soma pelo serviço; com titulado zero o saldo volta ao valor integral
        assertThat(DocumentoFinanceiroCalculo.saldo(v("1000"), v("0.00")).saldoATitular()).isEqualByComparingTo("1000");
    }

    @Test
    void excedenteQuandoTituladoSuperaOAcerto() {
        Saldo s = DocumentoFinanceiroCalculo.saldo(v("800"), v("1000"));
        assertThat(s.saldoATitular()).isEqualByComparingTo("0");
        assertThat(s.excedente()).isEqualByComparingTo("200");
        assertThat(DocumentoFinanceiroCalculo.aviso(Tipo.ACERTO, s)).contains("R$").contains("supera");
        assertThat(DocumentoFinanceiroCalculo.aviso(Tipo.ACERTO, DocumentoFinanceiroCalculo.saldo(v("800"), v("800")))).isNull();
        assertThat(DocumentoFinanceiroCalculo.excedenteRecebido(v("800"), v("900"))).isEqualByComparingTo("100");
        assertThat(DocumentoFinanceiroCalculo.excedenteRecebido(v("800"), v("700"))).isEqualByComparingTo("0");
    }

    @Test
    void saldoZeroNaoGera() {
        String motivo = DocumentoFinanceiroCalculo.motivoBloqueio(situacao(Tipo.ACERTO, true, true, true, "800", false, false, "800"));
        assertThat(motivo).contains("totalmente titulado");
    }

    @Test
    void excedenteNaoGeraEInformaOValor() {
        String motivo = DocumentoFinanceiroCalculo.motivoBloqueio(situacao(Tipo.ACERTO, true, true, true, "800", false, false, "1000"));
        assertThat(motivo).contains("acima do seu valor").contains("200");
    }

    @Test
    void regrasDeBloqueioDaGeracao() {
        // pré-acerto sem consignação gera título normalmente; acerto sem consignação continua bloqueado
        assertThat(DocumentoFinanceiroCalculo.motivoBloqueio(situacao(Tipo.PRE_ACERTO, false, false, true, "100", false, false, "0"))).isNull();
        assertThat(DocumentoFinanceiroCalculo.motivoBloqueio(situacao(Tipo.ACERTO, false, true, true, "100", false, false, "0")))
                .isEqualTo("O acerto não possui consignação e não gera título a receber.");
        assertThat(DocumentoFinanceiroCalculo.motivoBloqueio(situacao(Tipo.ACERTO, true, false, true, "100", false, false, "0")))
                .isEqualTo("O acerto precisa estar confirmado para gerar título a receber.");
        assertThat(DocumentoFinanceiroCalculo.motivoBloqueio(situacao(Tipo.PRE_ACERTO, true, false, true, "0", false, false, "0")))
                .contains("valor maior que zero");
        assertThat(DocumentoFinanceiroCalculo.motivoBloqueio(situacao(Tipo.PRE_ACERTO, true, false, true, "-5", false, false, "0")))
                .contains("valor maior que zero");
        assertThat(DocumentoFinanceiroCalculo.motivoBloqueio(situacao(Tipo.PRE_ACERTO, true, false, true, "100", true, false, "40")))
                .isEqualTo("O pré-acerto já possui título a receber ativo.");
        assertThat(DocumentoFinanceiroCalculo.motivoBloqueio(situacao(Tipo.ACERTO, true, true, true, "100", true, true, "40")))
                .isEqualTo("O acerto já possui título a receber ativo.");
        assertThat(DocumentoFinanceiroCalculo.motivoBloqueio(situacao(Tipo.PRE_ACERTO, true, false, true, "100", false, true, "0")))
                .isEqualTo("A consignação já possui título a receber do acerto.");
        assertThat(DocumentoFinanceiroCalculo.motivoBloqueio(situacao(Tipo.ACERTO, true, true, false, "100", false, false, "0")))
                .isEqualTo("O cliente \"Maria\" está desativado.");
        assertThat(DocumentoFinanceiroCalculo.motivoBloqueio(situacao(Tipo.ACERTO, true, true, true, "100", false, false, "0"))).isNull();
        assertThat(DocumentoFinanceiroCalculo.motivoBloqueio(situacao(Tipo.PRE_ACERTO, true, false, true, "100", false, false, "0"))).isNull();
    }

    @Test
    void descricaoAutomaticaEmMaiusculas() {
        assertThat(DocumentoFinanceiroCalculo.descricaoAutomatica(Tipo.PRE_ACERTO, 123L, 206L)).isEqualTo("CONS. Nº 123 - PRÉ-ACERTO Nº 206");
        assertThat(DocumentoFinanceiroCalculo.descricaoAutomatica(Tipo.PRE_ACERTO, null, 12L)).isEqualTo("PRÉ-ACERTO Nº 12");
        assertThat(DocumentoFinanceiroCalculo.descricaoAutomatica(Tipo.ACERTO, 5L, 7L)).isEqualTo("CONS. Nº 5 - ACERTO Nº 7");
    }

    @Test
    void tituloDeConsignacaoNaoAlteraValorNemCliente() {
        ContaReceberCalculo.validarEdicaoDeTituloDeConsignacao(false, true);
        ContaReceberCalculo.validarEdicaoDeTituloDeConsignacao(true, false);
        assertThatThrownBy(() -> ContaReceberCalculo.validarEdicaoDeTituloDeConsignacao(true, true))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("Cancele o título e gere novamente");
    }

    @Test
    void tituloZeradoDoAcerto() {
        assertThat(DocumentoFinanceiroCalculo.geraTituloZerado(new java.math.BigDecimal("0.00"), false)).isTrue();
        assertThat(DocumentoFinanceiroCalculo.geraTituloZerado(new java.math.BigDecimal("0.004"), false)).isTrue();
        assertThat(DocumentoFinanceiroCalculo.geraTituloZerado(new java.math.BigDecimal("0.01"), false)).isFalse();
        assertThat(DocumentoFinanceiroCalculo.geraTituloZerado(new java.math.BigDecimal("-5.00"), false)).isFalse();
        // com título de pré-acerto (ou do próprio acerto) ativo não gera: há excedente / já existe título
        assertThat(DocumentoFinanceiroCalculo.geraTituloZerado(java.math.BigDecimal.ZERO, true)).isFalse();
        assertThat(DocumentoFinanceiroCalculo.tituloZerado(new java.math.BigDecimal("0.00"))).isTrue();
        assertThat(DocumentoFinanceiroCalculo.tituloZerado(new java.math.BigDecimal("10.00"))).isFalse();
        assertThat(DocumentoFinanceiroCalculo.OBSERVACAO_TITULO_ZERADO).isEqualTo("Acerto sem valor a pagar. Título registrado automaticamente, sem cobrança.");
    }

    @Test
    void acertoComTituloZeradoInformaTituloAtivoEmVezDeValorZero() {
        assertThat(DocumentoFinanceiroCalculo.motivoBloqueio(situacao(Tipo.ACERTO, true, true, true, "0", true, false, "0")))
                .isEqualTo("O acerto já possui título a receber ativo.");
        assertThat(DocumentoFinanceiroCalculo.motivoBloqueio(situacao(Tipo.ACERTO, true, true, true, "0", false, false, "0")))
                .isEqualTo("O acerto deve ter valor maior que zero para gerar título a receber.");
    }
}
