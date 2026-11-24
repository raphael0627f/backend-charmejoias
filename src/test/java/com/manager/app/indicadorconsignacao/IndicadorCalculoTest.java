package com.manager.app.indicadorconsignacao;

import com.manager.app.indicadorconsignacao.dto.IndicadorDTOs;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// cenários numéricos conferíveis à mão
class IndicadorCalculoTest {

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    @Test
    void descontoEComissaoSaoPonderadosPeloVendidoENaoMediaSimples() {
        // acerto A: vendido 1000, comissão 300 (30%); acerto B: vendido 3000, comissão 300 (10%).
        // média simples dos % seria 20%; ponderada = 600 / 4000 = 15%.
        IndicadorDTOs.Vendas r = IndicadorCalculo.vendas(2, v("4000"), v("600"));
        assertEquals(v("15.0"), r.descontoMedioPercentual());
        assertEquals(v("15.0"), r.comissaoMediaPercentual());
        assertEquals(v("300.00"), r.comissaoMediaValor());
        assertEquals(v("2000.00"), r.valorVendidoMedioPorAcerto());
        assertEquals(v("4000.00"), r.valorVendidoTotal());
        assertEquals(2L, r.base().acertos());
    }

    @Test
    void semAcertosDevolveNuloEBaseZeroSemDividirPorZero() {
        IndicadorDTOs.Vendas r = IndicadorCalculo.vendas(0, BigDecimal.ZERO, BigDecimal.ZERO);
        assertNull(r.descontoMedioPercentual());
        assertNull(r.comissaoMediaValor());
        assertNull(r.comissaoMediaPercentual());
        assertNull(r.valorVendidoMedioPorAcerto());
        assertEquals(0L, r.base().acertos());
        assertEquals(v("0.00"), r.valorVendidoTotal());
        // vendido zero com acertos (tudo devolvido) também não divide por zero no percentual
        assertNull(IndicadorCalculo.vendas(1, BigDecimal.ZERO, BigDecimal.ZERO).comissaoMediaPercentual());
    }

    @Test
    void faixasDeAtrasoNosLimites() {
        assertEquals("1-30", IndicadorCalculo.faixa(1));
        assertEquals("1-30", IndicadorCalculo.faixa(30));
        assertEquals("31-60", IndicadorCalculo.faixa(31));
        assertEquals("31-60", IndicadorCalculo.faixa(60));
        assertEquals(">60", IndicadorCalculo.faixa(61));
    }

    @Test
    void inadimplenciaDevolveAsTresFaixasSempreEmOrdemESomaOsTotais() {
        // parcial: o valor de cada faixa já é só o saldo em aberto (a consulta soma valor - valor_recebido)
        IndicadorDTOs.Inadimplencia r = IndicadorCalculo.inadimplencia(Map.of("31-60", v("150.50"), "1-30", v("49.50")), Map.of("31-60", 2L, "1-30", 1L));
        assertEquals(List.of("1-30", "31-60", ">60"), r.faixas().stream().map(IndicadorDTOs.Faixa::faixa).toList());
        assertEquals(v("49.50"), r.faixas().get(0).valor());
        assertEquals(0, r.faixas().get(2).valor().signum());
        assertEquals(0L, r.faixas().get(2).quantidade());
        assertEquals(v("200.00"), r.valorTotal());
        assertEquals(3L, r.quantidadeTitulos());
        assertEquals(3L, r.base().titulos());
    }

    @Test
    void inadimplenciaVaziaTemFaixasZeradas() {
        IndicadorDTOs.Inadimplencia r = IndicadorCalculo.inadimplencia(Map.of(), Map.of());
        assertEquals(3, r.faixas().size());
        assertEquals(0, r.valorTotal().signum());
        assertEquals(0L, r.quantidadeTitulos());
    }

    @Test
    void prazoMedioIncluiAdiantadosEAtrasoMedioSoOsAtrasados() {
        // 4 títulos pagos: -2 (adiantado), 0 (no dia), +6, +10  => soma 14, médio 3,5; atrasados: 2 com soma 16 => 8,0; no prazo (<=0): 2 de 4 = 50%
        IndicadorDTOs.PrazoRecebimento r = IndicadorCalculo.prazo(4, 14, 2, 16, 2);
        assertEquals(v("3.5"), r.diasMedioAposVencimento());
        assertEquals(v("8.0"), r.atrasoMedioDosPagos());
        assertEquals(v("50.0"), r.pagosNoPrazoPercentual());
        assertEquals(4L, r.base().titulos());
    }

    @Test
    void prazoSemPagosOuSemAtrasadosNaoDivideporZero() {
        IndicadorDTOs.PrazoRecebimento vazio = IndicadorCalculo.prazo(0, 0, 0, 0, 0);
        assertNull(vazio.diasMedioAposVencimento());
        assertNull(vazio.atrasoMedioDosPagos());
        assertNull(vazio.pagosNoPrazoPercentual());
        assertEquals(0L, vazio.base().titulos());
        IndicadorDTOs.PrazoRecebimento todosNoPrazo = IndicadorCalculo.prazo(2, -3, 0, 0, 2);
        assertEquals(v("-1.5"), todosNoPrazo.diasMedioAposVencimento());
        assertNull(todosNoPrazo.atrasoMedioDosPagos());
        assertEquals(v("100.0"), todosNoPrazo.pagosNoPrazoPercentual());
    }

    @Test
    void giroEhValorVendidoSobreOTotalQueSaiu() {
        // vendido 750, devolvido 250: 75% do que foi deixado foi vendido
        IndicadorDTOs.Giro g = IndicadorCalculo.giro(3, v("750"), v("250"), 15, 5);
        assertEquals(v("75.0"), g.percentualVendido());
        assertEquals(v("750.00"), g.valorVendido());
        assertEquals(v("250.00"), g.valorDevolvido());
        assertEquals(15L, g.quantidadeVendida());
        assertEquals(5L, g.quantidadeDevolvida());
        IndicadorDTOs.Giro vazio = IndicadorCalculo.giro(0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        assertNull(vazio.percentualVendido());
        assertNull(vazio.valorVendido());
        assertEquals(0L, vazio.base().acertos());
    }

    @Test
    void mesesDoPeriodoIncluindoOsSemDados() {
        assertEquals(List.of("2026-10", "2026-11", "2026-12", "2027-01"), IndicadorCalculo.meses(LocalDate.parse("2026-10-15"), LocalDate.parse("2027-01-02")));
        assertEquals(List.of("2026-11"), IndicadorCalculo.meses(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30")));
    }

    @Test
    void percentualEMediaSeguros() {
        assertNull(IndicadorCalculo.percentual(v("1"), BigDecimal.ZERO));
        assertNull(IndicadorCalculo.media(v("10"), 0));
        assertEquals(v("3.33"), IndicadorCalculo.media(v("10"), 3));
        assertEquals(v("33.3"), IndicadorCalculo.percentual(v("1"), v("3")));
    }
}
