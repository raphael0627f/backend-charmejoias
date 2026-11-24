package com.manager.app.fluxocaixa;

import com.manager.app.financeiro.SaldoVisibilidade;
import com.manager.app.fluxocaixa.FluxoCaixaCalculo.Visao;
import com.manager.app.fluxocaixa.FluxoCaixaRepository.Lado;
import com.manager.app.fluxocaixa.dto.FluxoCaixaDTOs;
import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// orquestração do fluxo de caixa com o repositório simulado: saldo oculto, filtros de título, previsto a partir de hoje e validações do detalhe
class FluxoCaixaServiceTest {

    private static final LocalDate HOJE = LocalDate.parse("2026-11-24");
    private final FluxoCaixaRepository repo = mock(FluxoCaixaRepository.class);
    private final SaldoVisibilidade saldos = mock(SaldoVisibilidade.class);
    private FluxoCaixaService service;

    private static FluxoCaixaFiltro filtro() {
        return new FluxoCaixaFiltro(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"), null, null, null, null, null, null);
    }

    @BeforeEach
    void preparar() {
        service = new FluxoCaixaService(repo, saldos);
        when(saldos.podeVerSaldo()).thenReturn(true);
        when(repo.realizadoReceber(any(), any(), any())).thenReturn(Map.of(LocalDate.parse("2026-11-10"), new BigDecimal("200.00")));
        when(repo.realizadoPagar(any(), any(), any())).thenReturn(Map.of(LocalDate.parse("2026-11-12"), new BigDecimal("80.00")));
        when(repo.previstoReceber(any(), any(), any())).thenReturn(Map.of(LocalDate.parse("2026-11-28"), new BigDecimal("60.00")));
        when(repo.previstoPagar(any(), any(), any())).thenReturn(new FluxoCaixaRepository.PrevistoPagar(Map.of(LocalDate.parse("2026-11-29"), new BigDecimal("25.00")),
                Map.of(LocalDate.parse("2026-11-29"), new BigDecimal("25.00"))));
        when(repo.atrasados(eq(Lado.RECEBER), any(), any())).thenReturn(new FluxoCaixaRepository.AtrasadoLinha(new BigDecimal("33.00"), 2));
        when(repo.atrasados(eq(Lado.PAGAR), any(), any())).thenReturn(new FluxoCaixaRepository.AtrasadoLinha(BigDecimal.ZERO, 0));
        when(repo.saldoAte(anyList(), any())).thenReturn(new BigDecimal("1000.00"));
        when(repo.movimentacaoLiquida(anyList(), any(), any())).thenReturn(Map.of(LocalDate.parse("2026-11-10"), new BigDecimal("200.00"), LocalDate.parse("2026-11-12"), new BigDecimal("-80.00")));
        when(repo.bancos(anyList())).thenReturn(List.of());
        when(repo.categoriasRealizado(any(), any(), any(), any())).thenReturn(List.of());
        when(repo.categoriasAberto(any(), any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void resumoCompletoComSaldos() {
        FluxoCaixaDTOs.Resposta r = service.resumo(filtro(), Visao.DIARIO, HOJE);
        assertTrue(r.saldosVisiveis());
        assertEquals(30, r.serie().size());
        assertEquals(new BigDecimal("200.00"), r.resumo().entradasRealizadas());
        assertEquals(new BigDecimal("80.00"), r.resumo().saidasRealizadas());
        assertEquals(new BigDecimal("1120.00"), r.resumo().saldoFinalRealizado());
        assertEquals(new BigDecimal("1155.00"), r.resumo().saldoFinalProjetado()); // 1120 + 60 - 25
        assertEquals(new BigDecimal("25.00"), r.resumo().saidasPrevistasEstimadas());
        assertEquals(new BigDecimal("33.00"), r.resumo().atrasadosReceber().valor());
        assertNotNull(r.porBanco());
        // o previsto só começa em HOJE (nunca em dias passados)
        verify(repo).previstoReceber(any(), eq(HOJE), eq(LocalDate.parse("2026-11-30")));
    }

    @Test
    void semPermissaoDeSaldoNaoConsultaNemDevolveSaldos() {
        when(saldos.podeVerSaldo()).thenReturn(false);
        FluxoCaixaDTOs.Resposta r = service.resumo(filtro(), Visao.DIARIO, HOJE);
        assertFalse(r.saldosVisiveis());
        assertNull(r.porBanco());
        assertNull(r.resumo().saldoInicial());
        assertNull(r.resumo().saldoFinalRealizado());
        assertNull(r.resumo().saldoFinalProjetado());
        assertNull(r.serie().get(0).saldoAcumuladoRealizado());
        assertNull(r.saldosIndisponiveisMotivo());
        assertEquals(new BigDecimal("200.00"), r.resumo().entradasRealizadas());
        verify(repo, never()).saldoAte(anyList(), any());
        verify(repo, never()).movimentacaoLiquida(anyList(), any(), any());
        verify(repo, never()).saldoPorBancoAte(anyList(), any());
    }

    @Test
    void filtroDeCategoriaOcultaSaldosComMotivo() {
        FluxoCaixaFiltro f = new FluxoCaixaFiltro(null, null, null, List.of(3L), null, null, null, null);
        FluxoCaixaDTOs.Resposta r = service.resumo(f, Visao.MENSAL, HOJE);
        assertFalse(r.saldosVisiveis());
        assertEquals(FluxoCaixaService.MOTIVO_FILTRO, r.saldosIndisponiveisMotivo());
        assertNull(r.resumo().saldoFinalRealizado());
        // período padrão = mês atual
        assertEquals(LocalDate.parse("2026-11-01"), r.periodo().inicio());
        assertEquals(LocalDate.parse("2026-11-30"), r.periodo().fim());
    }

    @Test
    void favorecidoRetiraContasAReceber() {
        FluxoCaixaFiltro f = new FluxoCaixaFiltro(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"), null, null, null, null, null, "Energia SA");
        FluxoCaixaDTOs.Resposta r = service.resumo(f, Visao.DIARIO, HOJE);
        verify(repo, never()).realizadoReceber(any(), any(), any());
        verify(repo, never()).previstoReceber(any(), any(), any());
        verify(repo, never()).atrasados(eq(Lado.RECEBER), any(), any());
        assertEquals(0, r.resumo().entradasRealizadas().signum());
        assertTrue(r.porCategoria().receitas().isEmpty());
    }

    @Test
    void periodoTotalmentePassadoNaoTemPrevisto() {
        FluxoCaixaFiltro f = new FluxoCaixaFiltro(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), null, null, null, null, null, null);
        FluxoCaixaDTOs.Resposta r = service.resumo(f, Visao.SEMANAL, HOJE);
        verify(repo, never()).previstoReceber(any(), any(), any());
        verify(repo, never()).previstoPagar(any(), any(), any());
        assertEquals(0, r.resumo().entradasPrevistas().signum());
    }

    @Test
    void bancoFiltradoAvisaQueOPrevistoNaoTemBanco() {
        FluxoCaixaFiltro f = new FluxoCaixaFiltro(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"), List.of(1L), null, null, null, null, null);
        assertTrue(service.resumo(f, Visao.DIARIO, HOJE).previstoSemBanco());
        assertFalse(service.resumo(filtro(), Visao.DIARIO, HOJE).previstoSemBanco());
    }

    @Test
    void detalheValidaParametros() {
        assertThrows(ItemInvalidoException.class, () -> service.detalhe(filtro(), Visao.DIARIO, "2026-11-10", "OUTRO", "REALIZADO", null, 0, 20, HOJE));
        assertThrows(ItemInvalidoException.class, () -> service.detalhe(filtro(), Visao.DIARIO, "2026-11-10", "ENTRADA", "TALVEZ", null, 0, 20, HOJE));
        ItemInvalidoException e = assertThrows(ItemInvalidoException.class, () -> service.detalhe(filtro(), Visao.DIARIO, "2026-11-10", "ENTRADA", "REALIZADO", "xpto", 0, 20, HOJE));
        assertEquals("Campo de ordenação inválido.", e.getMessage());
        assertThrows(ItemInvalidoException.class, () -> service.detalhe(filtro(), Visao.DIARIO, "2026-11-10", "ENTRADA", "REALIZADO", "valor,cima", 0, 20, HOJE));
        assertThrows(ItemInvalidoException.class, () -> service.detalhe(filtro(), Visao.DIARIO, "2026-11-10", "ENTRADA", "REALIZADO", null, 0, 101, HOJE));
        assertThrows(ItemInvalidoException.class, () -> service.detalhe(filtro(), Visao.DIARIO, "2026-11-10", "ENTRADA", "REALIZADO", null, -1, 20, HOJE));
        assertThrows(ItemInvalidoException.class, () -> service.detalhe(filtro(), Visao.DIARIO, null, "ENTRADA", "REALIZADO", null, 0, 20, HOJE));
        assertThrows(ItemInvalidoException.class, () -> service.detalhe(filtro(), Visao.DIARIO, "2027-01-01", "ENTRADA", "REALIZADO", null, 0, 20, HOJE));
    }

    @Test
    void detalhePrevistoDeDiaPassadoEhVazioSemConsultar() {
        FluxoCaixaDTOs.Detalhe d = service.detalhe(filtro(), Visao.DIARIO, "2026-11-10", "ENTRADA", "PREVISTO", null, 0, 20, HOJE);
        assertEquals(0, d.totalElementos());
        assertTrue(d.itens().isEmpty());
        verify(repo, never()).detalhe(any(), anyString(), any(), any(), any(), any(), anyString(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    void detalheAtrasadoIgnoraOPeriodoEDelegaAoRepositorio() {
        FluxoCaixaDTOs.Detalhe esperado = new FluxoCaixaDTOs.Detalhe(0, 20, 1, 1, new BigDecimal("33.00"), List.of());
        when(repo.detalhe(eq(Lado.RECEBER), eq("ATRASADO"), any(), any(), any(), eq(HOJE), eq("data"), eq(false), eq(0), eq(20))).thenReturn(esperado);
        assertSame(esperado, service.detalhe(filtro(), Visao.DIARIO, null, "entrada", "atrasado", null, 0, 20, HOJE));
    }

    @Test
    void periodoFuturoTemSaldoInicialProjetadoComOPrevistoAteAVesperaDoInicio() {
        // saldo realizado hoje 1000; previsto entre hoje e 30/11: +60 e -25 => saldo inicial projetado de dezembro = 1035
        FluxoCaixaFiltro dezembro = new FluxoCaixaFiltro(LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-31"), null, null, null, null, null, null);
        FluxoCaixaDTOs.Resposta r = service.resumo(dezembro, Visao.MENSAL, HOJE);
        assertEquals(new BigDecimal("1035.00"), r.resumo().saldoInicial());
        assertEquals(new BigDecimal("35.00"), r.resumo().previstoAntesDoPeriodo());
        verify(repo).previstoReceber(any(), eq(HOJE), eq(LocalDate.parse("2026-11-30")));
    }

    @Test
    void periodoQueContemHojeNaoTemPrevistoAntesDoPeriodo() {
        FluxoCaixaDTOs.Resposta r = service.resumo(filtro(), Visao.DIARIO, HOJE);
        assertNull(r.resumo().previstoAntesDoPeriodo());
        assertEquals(new BigDecimal("1000.00"), r.resumo().saldoInicial());
    }

    @Test
    void periodoMaiorQueOLimiteTemMensagemFormalClara() {
        FluxoCaixaFiltro longo = new FluxoCaixaFiltro(LocalDate.parse("2025-01-01"), LocalDate.parse("2026-11-30"), null, null, null, null, null, null);
        assertEquals("O período selecionado excede o limite de 366 dias. Reduza o intervalo entre as datas e tente novamente.",
                assertThrows(ItemInvalidoException.class, () -> service.resumo(longo, Visao.MENSAL, HOJE)).getMessage());
    }

    @Test
    void detalheDeEntradaComFavorecidoEhVazio() {
        FluxoCaixaFiltro f = new FluxoCaixaFiltro(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"), null, null, null, null, UUID.randomUUID(), "Energia SA");
        assertEquals(0, service.detalhe(f, Visao.DIARIO, "2026-11-10", "ENTRADA", "REALIZADO", null, 0, 20, HOJE).totalElementos());
    }
}
