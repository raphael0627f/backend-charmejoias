package com.manager.app.financeiro;

import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// faixas de vencimento, atraso e totais da situação do cliente
class SituacaoClienteCalculoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 16);

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    private static SituacaoClienteCalculo.TituloAberto t(long id, String vencimento, String aberto) {
        return new SituacaoClienteCalculo.TituloAberto(id, id + 100, d(vencimento), new BigDecimal(aberto));
    }

    @Test
    void vencidoEhSoOAnteriorAHoje() {
        assertThat(SituacaoClienteCalculo.vencido(d("2026-11-15"), HOJE)).isTrue();
        assertThat(SituacaoClienteCalculo.vencido(HOJE, HOJE)).isFalse();
        assertThat(SituacaoClienteCalculo.vencido(d("2026-11-17"), HOJE)).isFalse();
    }

    @Test
    void faixasDeSeteETrintaDiasSaoInclusivasEComecamHoje() {
        assertThat(SituacaoClienteCalculo.venceEm(HOJE, HOJE, 7)).isTrue();
        assertThat(SituacaoClienteCalculo.venceEm(d("2026-11-23"), HOJE, 7)).isTrue();
        assertThat(SituacaoClienteCalculo.venceEm(d("2026-11-24"), HOJE, 7)).isFalse();
        assertThat(SituacaoClienteCalculo.venceEm(d("2026-11-15"), HOJE, 7)).isFalse();
        assertThat(SituacaoClienteCalculo.venceEm(d("2026-12-16"), HOJE, 30)).isTrue();
        assertThat(SituacaoClienteCalculo.venceEm(d("2026-12-17"), HOJE, 30)).isFalse();
    }

    @Test
    void diasDeAtraso() {
        assertThat(SituacaoClienteCalculo.diasDeAtraso(d("2026-11-04"), HOJE)).isEqualTo(12);
        assertThat(SituacaoClienteCalculo.diasDeAtraso(d("2026-11-15"), HOJE)).isEqualTo(1);
        assertThat(SituacaoClienteCalculo.diasDeAtraso(HOJE, HOJE)).isZero();
        assertThat(SituacaoClienteCalculo.diasDeAtraso(d("2026-12-01"), HOJE)).isZero();
    }

    @Test
    void resumoSomaFaixasEEncontraOMaisAntigoVencido() {
        SituacaoClienteCalculo.Resumo r = SituacaoClienteCalculo.resumir(List.of(
                t(1, "2026-11-04", "400.00"),   // vencido há 12 dias
                t(2, "2026-11-10", "100.50"),   // vencido há 6 dias
                t(3, "2026-11-16", "50.00"),    // vence hoje (7 e 30 dias)
                t(4, "2026-11-20", "300.00"),   // 7 e 30 dias
                t(5, "2026-12-10", "200.00"),   // só 30 dias
                t(6, "2027-02-01", "1000.00"),  // só em aberto
                t(7, "2026-11-01", "0.00")      // sem saldo: ignorado
        ), HOJE);
        assertThat(r.emAberto().valor()).isEqualByComparingTo("2050.50");
        assertThat(r.emAberto().quantidade()).isEqualTo(6);
        assertThat(r.vencido().valor()).isEqualByComparingTo("500.50");
        assertThat(r.vencido().quantidade()).isEqualTo(2);
        assertThat(r.vencemEm7Dias().valor()).isEqualByComparingTo("350.00");
        assertThat(r.vencemEm7Dias().quantidade()).isEqualTo(2);
        assertThat(r.vencemEm30Dias().valor()).isEqualByComparingTo("550.00");
        assertThat(r.vencemEm30Dias().quantidade()).isEqualTo(3);
        assertThat(r.diasMaiorAtraso()).isEqualTo(12);
        assertThat(r.maisAntigoVencido().id()).isEqualTo(1L);
    }

    @Test
    void empateNoMaisAntigoFicaComOMenorId() {
        SituacaoClienteCalculo.Resumo r = SituacaoClienteCalculo.resumir(List.of(t(9, "2026-11-01", "10"), t(3, "2026-11-01", "20")), HOJE);
        assertThat(r.maisAntigoVencido().id()).isEqualTo(3L);
        assertThat(r.diasMaiorAtraso()).isEqualTo(15);
    }

    @Test
    void clienteSemTitulosTemTudoZerado() {
        SituacaoClienteCalculo.Resumo r = SituacaoClienteCalculo.resumir(List.of(), HOJE);
        assertThat(r.emAberto().valor()).isEqualByComparingTo("0");
        assertThat(r.emAberto().quantidade()).isZero();
        assertThat(r.vencido().quantidade()).isZero();
        assertThat(r.diasMaiorAtraso()).isZero();
        assertThat(r.maisAntigoVencido()).isNull();
    }

    @Test
    void periodoPadraoSaoOsUltimosDozeMeses() {
        LocalDate[] p = SituacaoClienteCalculo.periodo(null, null, HOJE);
        assertThat(p[0]).isEqualTo(d("2025-11-16"));
        assertThat(p[1]).isEqualTo(HOJE);
        LocalDate[] q = SituacaoClienteCalculo.periodo(null, d("2026-03-31"), HOJE);
        assertThat(q[0]).isEqualTo(d("2025-03-31"));
        LocalDate[] r = SituacaoClienteCalculo.periodo(d("2026-01-01"), d("2026-02-01"), HOJE);
        assertThat(r[0]).isEqualTo(d("2026-01-01"));
        assertThatThrownBy(() -> SituacaoClienteCalculo.periodo(d("2026-03-01"), d("2026-02-01"), HOJE)).isInstanceOf(ItemInvalidoException.class);
    }
}
