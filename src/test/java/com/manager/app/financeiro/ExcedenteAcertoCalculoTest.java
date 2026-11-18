package com.manager.app.financeiro;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcedenteAcertoCalculoTest {

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    @Test
    void excedenteEORecebidoAcimaDoValorDoAcerto() {
        assertThat(ExcedenteAcertoCalculo.excedente(v("630.00"), v("450.00"))).isEqualByComparingTo("180.00");
        assertThat(ExcedenteAcertoCalculo.excedente(v("500"), v("450"))).isEqualByComparingTo("50.00");
        assertThat(ExcedenteAcertoCalculo.excedente(v("630.004"), v("450.00"))).isEqualByComparingTo("180.00");
    }

    @Test
    void semExcedenteQuandoORecebidoNaoSuperaOAcerto() {
        assertThat(ExcedenteAcertoCalculo.excedente(v("0"), v("450"))).isEqualByComparingTo("0.00");
        assertThat(ExcedenteAcertoCalculo.excedente(v("450"), v("450"))).isEqualByComparingTo("0.00");
        assertThat(ExcedenteAcertoCalculo.excedente(v("100"), v("450"))).isEqualByComparingTo("0.00");
        // acerto com valor negativo (desconto maior que o total) conta como zero: não inventa excedente além do recebido
        assertThat(ExcedenteAcertoCalculo.excedente(v("0"), v("-10"))).isEqualByComparingTo("0.00");
        assertThat(ExcedenteAcertoCalculo.excedente(v("630"), v("-10"))).isEqualByComparingTo("630.00");
    }

    @Test
    void ajusteDoTituloDoPreAcerto() {
        // T 1200 > A 450, nada recebido: reduz para 450
        ExcedenteAcertoCalculo.Ajuste a = ExcedenteAcertoCalculo.ajuste(v("1200"), v("0"), v("450"));
        assertThat(a.valorNovo()).isEqualByComparingTo("450.00");
        assertThat(a.reducao()).isEqualByComparingTo("750.00");
        // recebido 500 > A 450: reduz para o recebido (500) e o excedente será 50
        ExcedenteAcertoCalculo.Ajuste b = ExcedenteAcertoCalculo.ajuste(v("1200"), v("500"), v("450"));
        assertThat(b.valorNovo()).isEqualByComparingTo("500.00");
        assertThat(b.reducao()).isEqualByComparingTo("700.00");
        // sem ajuste: título dentro do acerto ou título integralmente recebido
        assertThat(ExcedenteAcertoCalculo.ajuste(v("450"), v("0"), v("450"))).isNull();
        assertThat(ExcedenteAcertoCalculo.ajuste(v("630"), v("630"), v("450"))).isNull();
        // acerto zero e nada recebido: o título do pré-acerto é cancelado (valor novo 0) com a pergunta própria
        ExcedenteAcertoCalculo.Ajuste c = ExcedenteAcertoCalculo.ajuste(v("630"), v("0"), v("0"));
        assertThat(c.cancelaTitulo()).isTrue();
        assertThat(c.reducao()).isEqualByComparingTo("630.00");
        assertThat(ExcedenteAcertoCalculo.perguntaDoAjuste(c)).isEqualTo("O título do pré-acerto será cancelado, pois nada foi pago e o acerto não tem valor a pagar.");
        assertThat(ExcedenteAcertoCalculo.ajuste(v("630"), v("100"), v("0")).cancelaTitulo()).isFalse();
        // acerto zero com algo recebido: reduz para o recebido
        assertThat(ExcedenteAcertoCalculo.ajuste(v("630"), v("100"), v("0")).valorNovo()).isEqualByComparingTo("100.00");
    }

    @Test
    void novosValoresConsomeOSaldoAbertoDaUltimaParcelaParaTras() {
        // 3 parcelas de 100; reduzir 150: última zera (será cancelada), a do meio vai a 50
        assertThat(ExcedenteAcertoCalculo.novosValores(List.of(v("100"), v("100"), v("100")), List.of(v("0"), v("0"), v("0")), v("150")))
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class).containsExactly(v("100"), v("50"), v("0"));
        // parcela com recebimento nunca fica abaixo do recebido
        assertThat(ExcedenteAcertoCalculo.novosValores(List.of(v("100"), v("100")), List.of(v("100"), v("40")), v("100")))
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class).containsExactly(v("100"), v("40"));
    }

    @Test
    void perguntaFormalDoAjuste() {
        ExcedenteAcertoCalculo.Ajuste a = ExcedenteAcertoCalculo.ajuste(v("1200"), v("0"), v("450"));
        assertThat(ExcedenteAcertoCalculo.perguntaDoAjuste(a)).isEqualTo("O título do pré-acerto (R$ 1.200,00) está acima do acerto (R$ 450,00). Ajustar o título para R$ 450,00?");
    }
}
