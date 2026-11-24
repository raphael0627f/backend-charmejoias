package com.manager.app.fluxocaixa;

import com.manager.app.fluxocaixa.FluxoCaixaCalculo.Dados;
import com.manager.app.fluxocaixa.FluxoCaixaCalculo.Visao;
import com.manager.app.fluxocaixa.dto.FluxoCaixaDTOs;
import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// cenários numéricos conferíveis à mão: realizado x previsto, atrasados em HOJE, saldo acumulado, semana e mês, saldos ocultos
class FluxoCaixaCalculoTest {

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    private static Dados dados(Map<LocalDate, BigDecimal> er, Map<LocalDate, BigDecimal> sr, Map<LocalDate, BigDecimal> ep, Map<LocalDate, BigDecimal> sp,
                               Map<LocalDate, BigDecimal> mov, String atrasR, long qR, String atrasP, long qP) {
        return new Dados(er, sr, ep, sp, Map.of(), mov, v(atrasR), qR, v(atrasP), qP);
    }

    @Test
    void diarioComSaldoAcumuladoRealizadoEProjetadoEAtrasadosSoEmHoje() {
        LocalDate hoje = d("2026-11-03");
        // 01/11: entrou 100 (realizado); 02/11: saiu 40 (realizado); 03/11 (hoje) nada; 04/11 previsto entrada 70 e saída 20; atrasados: receber 50 (2 títulos), pagar 10 (1)
        Dados dd = dados(Map.of(d("2026-11-01"), v("100")), Map.of(d("2026-11-02"), v("40")), Map.of(d("2026-11-04"), v("70")), Map.of(d("2026-11-04"), v("20")),
                Map.of(d("2026-11-01"), v("100"), d("2026-11-02"), v("-40")), "50", 2, "10", 1);
        FluxoCaixaCalculo.Resultado r = FluxoCaixaCalculo.calcular(dd, d("2026-11-01"), d("2026-11-05"), Visao.DIARIO, hoje, v("1000"));
        assertEquals(5, r.serie().size());
        // saldo realizado acumulado: 1100, 1060, 1060, 1060, 1060
        assertEquals(v("1100.00"), r.serie().get(0).saldoAcumuladoRealizado());
        assertEquals(v("1060.00"), r.serie().get(1).saldoAcumuladoRealizado());
        assertEquals(v("1060.00"), r.serie().get(4).saldoAcumuladoRealizado());
        // projetado: soma o previsto líquido (+50 em 04/11); atrasados NÃO entram
        assertEquals(v("1060.00"), r.serie().get(2).saldoAcumuladoProjetado());
        assertEquals(v("1110.00"), r.serie().get(3).saldoAcumuladoProjetado());
        // atrasados só no ponto de hoje (03/11), nos dois lados
        assertEquals(v("50.00"), r.serie().get(2).atrasadosReceber());
        assertEquals(v("10.00"), r.serie().get(2).atrasadosPagar());
        assertEquals(0, r.serie().get(1).atrasadosReceber().signum());
        FluxoCaixaDTOs.Resumo s = r.resumo();
        assertEquals(v("100.00"), s.entradasRealizadas());
        assertEquals(v("40.00"), s.saidasRealizadas());
        assertEquals(v("60.00"), s.resultadoRealizado());
        assertEquals(v("50.00"), s.resultadoPrevisto());
        assertEquals(v("1060.00"), s.saldoFinalRealizado());
        assertEquals(v("1110.00"), s.saldoFinalProjetado());
        assertEquals(v("1000.00"), s.saldoInicial());
        assertEquals(0, s.outrasMovimentacoes().signum());
        assertEquals(v("50.00"), s.atrasadosReceber().valor());
        assertEquals(2, s.atrasadosReceber().quantidade());
    }

    @Test
    void movimentacaoQueNaoEhRecebimentoNemPagamentoViraOutrasMovimentacoes() {
        // transferência líquida de +30 no dia 02/11 (movimentação sem recebimento/pagamento) entra no saldo e em "outras", nunca em entradas
        Dados dd = dados(Map.of(d("2026-11-01"), v("100")), Map.of(), Map.of(), Map.of(), Map.of(d("2026-11-01"), v("100"), d("2026-11-02"), v("30")), "0", 0, "0", 0);
        FluxoCaixaDTOs.Resumo s = FluxoCaixaCalculo.calcular(dd, d("2026-11-01"), d("2026-11-02"), Visao.DIARIO, d("2026-11-30"), v("0")).resumo();
        assertEquals(v("100.00"), s.entradasRealizadas());
        assertEquals(v("30.00"), s.outrasMovimentacoes());
        assertEquals(v("130.00"), s.saldoFinalRealizado());
    }

    @Test
    void saldosOcultosDevolvemNulosMasMantemEntradasESaidas() {
        Dados dd = dados(Map.of(d("2026-11-01"), v("100")), Map.of(d("2026-11-01"), v("30")), Map.of(), Map.of(), Map.of(), "0", 0, "0", 0);
        FluxoCaixaCalculo.Resultado r = FluxoCaixaCalculo.calcular(dd, d("2026-11-01"), d("2026-11-01"), Visao.DIARIO, d("2026-11-01"), null);
        assertNull(r.resumo().saldoInicial());
        assertNull(r.resumo().saldoFinalRealizado());
        assertNull(r.resumo().saldoFinalProjetado());
        assertNull(r.resumo().outrasMovimentacoes());
        assertNull(r.serie().get(0).saldoAcumuladoRealizado());
        assertNull(r.serie().get(0).saldoAcumuladoProjetado());
        assertEquals(v("100.00"), r.resumo().entradasRealizadas());
        assertEquals(v("70.00"), r.resumo().resultadoRealizado());
    }

    @Test
    void periodoSemMovimentoDevolveZeroEASerieContinua() {
        FluxoCaixaCalculo.Resultado r = FluxoCaixaCalculo.calcular(dados(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), "0", 0, "0", 0), d("2026-11-01"), d("2026-11-30"),
                Visao.DIARIO, d("2026-11-10"), v("5"));
        assertEquals(30, r.serie().size());
        assertTrue(r.serie().stream().allMatch(p -> p.entradasRealizadas().signum() == 0 && p.saidasPrevistas().signum() == 0));
        assertEquals(v("5.00"), r.serie().get(29).saldoAcumuladoRealizado());
    }

    @Test
    void semanalUsaSegundaComoChaveERecortaAsPontas() {
        // 2026-11-01 é domingo: a primeira semana (chave 26/10) vai de 01/11 a 01/11; depois 02/11-08/11, ...
        List<FluxoCaixaCalculo.Faixa> f = FluxoCaixaCalculo.faixas(d("2026-11-01"), d("2026-11-17"), Visao.SEMANAL);
        assertEquals(4, f.size());
        assertEquals("2026-10-26", f.get(0).id());
        assertEquals(d("2026-11-01"), f.get(0).de());
        assertEquals(d("2026-11-01"), f.get(0).ate());
        assertEquals("2026-11-02", f.get(1).id());
        assertEquals(d("2026-11-08"), f.get(1).ate());
        assertEquals(d("2026-11-17"), f.get(3).ate());
        // valores caem na semana certa
        Dados dd = dados(Map.of(d("2026-11-08"), v("10"), d("2026-11-09"), v("5")), Map.of(), Map.of(), Map.of(), Map.of(), "0", 0, "0", 0);
        FluxoCaixaCalculo.Resultado r = FluxoCaixaCalculo.calcular(dd, d("2026-11-01"), d("2026-11-17"), Visao.SEMANAL, d("2026-11-30"), null);
        assertEquals(v("10.00"), r.serie().get(1).entradasRealizadas());
        assertEquals(v("5.00"), r.serie().get(2).entradasRealizadas());
    }

    @Test
    void mensalSomaOMesInteiro() {
        Dados dd = dados(Map.of(d("2026-11-30"), v("10"), d("2026-12-01"), v("7")), Map.of(), Map.of(), Map.of(), Map.of(), "0", 0, "0", 0);
        FluxoCaixaCalculo.Resultado r = FluxoCaixaCalculo.calcular(dd, d("2026-11-15"), d("2027-01-10"), Visao.MENSAL, d("2026-11-20"), null);
        assertEquals(List.of("2026-11", "2026-12", "2027-01"), r.serie().stream().map(FluxoCaixaDTOs.Ponto::periodo).toList());
        assertEquals(v("10.00"), r.serie().get(0).entradasRealizadas());
        assertEquals(v("7.00"), r.serie().get(1).entradasRealizadas());
        assertEquals("11/2026", r.serie().get(0).rotulo());
    }

    @Test
    void atrasadosFicamZeradosNaSerieQuandoHojeNaoEstaNoPeriodo() {
        Dados dd = dados(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), "80", 3, "0", 0);
        FluxoCaixaCalculo.Resultado r = FluxoCaixaCalculo.calcular(dd, d("2026-12-01"), d("2026-12-03"), Visao.DIARIO, d("2026-11-24"), null);
        assertTrue(r.serie().stream().allMatch(p -> p.atrasadosReceber().signum() == 0));
        assertEquals(v("80.00"), r.resumo().atrasadosReceber().valor()); // o cartão continua sendo a posição de hoje
    }

    @Test
    void validaIntervaloEPeriodoDoDetalhe() {
        assertThrows(ItemInvalidoException.class, () -> FluxoCaixaCalculo.validarIntervalo(d("2026-11-10"), d("2026-11-09")));
        assertThrows(ItemInvalidoException.class, () -> FluxoCaixaCalculo.validarIntervalo(d("2025-01-01"), d("2026-11-09")));
        assertDoesNotThrow(() -> FluxoCaixaCalculo.validarIntervalo(d("2026-01-01"), d("2026-12-31")));
        assertThrows(ItemInvalidoException.class, () -> Visao.de("ANUAL"));
        FluxoCaixaCalculo.Faixa mes = FluxoCaixaCalculo.faixaDoTexto("2026-11", Visao.MENSAL, d("2026-11-10"), d("2026-12-31"));
        assertEquals(d("2026-11-10"), mes.de());
        assertEquals(d("2026-11-30"), mes.ate());
        assertThrows(ItemInvalidoException.class, () -> FluxoCaixaCalculo.faixaDoTexto("2026-11-10", Visao.MENSAL, d("2026-11-10"), d("2026-12-31")));
        assertThrows(ItemInvalidoException.class, () -> FluxoCaixaCalculo.faixaDoTexto("2027-03", Visao.MENSAL, d("2026-11-10"), d("2026-12-31")));
    }

    @Test
    void percentualNuncaDivideporZero() {
        assertEquals(v("0.0"), FluxoCaixaCalculo.percentual(v("5"), BigDecimal.ZERO));
        assertEquals(v("25.0"), FluxoCaixaCalculo.percentual(v("25"), v("100")));
        assertEquals(v("33.3"), FluxoCaixaCalculo.percentual(v("1"), v("3")));
    }
}
