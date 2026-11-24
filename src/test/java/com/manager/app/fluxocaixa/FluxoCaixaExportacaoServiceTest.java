package com.manager.app.fluxocaixa;

import com.manager.app.fluxocaixa.FluxoCaixaCalculo.Visao;
import com.manager.app.fluxocaixa.dto.FluxoCaixaDTOs;
import com.manager.app.shared.exception.ItemInvalidoException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

// geração real dos arquivos (POI e JasperReports) a partir de um relatório montado à mão: sem banco
class FluxoCaixaExportacaoServiceTest {

    private final FluxoCaixaExportacaoService service = new FluxoCaixaExportacaoService(mock(FluxoCaixaService.class), mock(FluxoCaixaRepository.class));

    private static FluxoCaixaExportacaoService.Relatorio relatorio() {
        FluxoCaixaExportacaoService.Secao resumo = new FluxoCaixaExportacaoService.Secao("Resumo", List.of("Indicador", "Valor"),
                List.of(List.of("Entradas realizadas", new BigDecimal("1234.50")), List.of("Atrasados a receber (títulos)", "3")), "Nota de teste.");
        FluxoCaixaExportacaoService.Secao detalhe = new FluxoCaixaExportacaoService.Secao("Detalhe", List.of("Tipo", "Situação", "Título nº", "Pessoa", "Categoria", "Banco", "Data", "Valor"),
                List.of(List.of("Entrada", "Realizado", "1180", "JOÃO DA SILVA", "Vendas", "Banco A", LocalDate.parse("2026-11-10"), new BigDecimal("99.90"))), null);
        return new FluxoCaixaExportacaoService.Relatorio("Fluxo de Caixa", "Período: 01/11/2026 a 30/11/2026 | Visão: diario", "Filtros: nenhum", "Gerado em 24/11/2026 10:00",
                List.of(resumo, detalhe));
    }

    @Test
    void geraXlsxComAbasValoresNumericosEDatas() throws Exception {
        byte[] bytes = service.xlsx(relatorio());
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(2, wb.getNumberOfSheets());
            assertEquals("Resumo", wb.getSheetName(0));
            // linha do cabeçalho da tabela: título, período, filtros, gerado, nota, vazia, cabeçalho => índice 6; primeira linha de dados = 7
            assertEquals("Entradas realizadas", wb.getSheetAt(0).getRow(7).getCell(0).getStringCellValue());
            assertEquals(1234.50, wb.getSheetAt(0).getRow(7).getCell(1).getNumericCellValue(), 0.001);
            assertEquals(99.90, wb.getSheetAt(1).getRow(6).getCell(7).getNumericCellValue(), 0.001);
            assertNotNull(wb.getSheetAt(1).getRow(6).getCell(6).getDateCellValue());
        }
    }

    @Test
    void geraPdfValido() {
        byte[] pdf = service.pdf(relatorio());
        assertTrue(pdf.length > 1000);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void formatoInvalidoEhRecusadoAntesDeConsultar() {
        ItemInvalidoException e = assertThrows(ItemInvalidoException.class, () -> service.exportar(
                new FluxoCaixaFiltro(null, null, null, null, null, null, null, null), Visao.DIARIO, "csv"));
        assertEquals("Formato inválido. Use xlsx ou pdf.", e.getMessage());
    }

    @Test
    void formataValoresEmReaisEDatasEmPortugues() {
        assertEquals("R$ 1.234,50", FluxoCaixaExportacaoService.texto(new BigDecimal("1234.50")));
        assertEquals("10/11/2026", FluxoCaixaExportacaoService.texto(LocalDate.parse("2026-11-10")));
        assertEquals("", FluxoCaixaExportacaoService.texto(null));
    }

    @Test
    void relatorioNaoExpoeSaldosQuandoOcultos() {
        FluxoCaixaDTOs.Resumo s = new FluxoCaixaDTOs.Resumo(null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, new FluxoCaixaDTOs.Atrasado(BigDecimal.ZERO, 0), new FluxoCaixaDTOs.Atrasado(BigDecimal.ZERO, 0), null);
        FluxoCaixaDTOs.Ponto p = new FluxoCaixaDTOs.Ponto("2026-11-01", "01/11", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        FluxoCaixaDTOs.Resposta r = new FluxoCaixaDTOs.Resposta(new FluxoCaixaDTOs.Periodo(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-01"), "DIARIO", LocalDate.parse("2026-11-01")),
                false, null, false, s, List.of(p), null, new FluxoCaixaDTOs.Categorias(List.of(), List.of()), null);
        FluxoCaixaRepository repo = mock(FluxoCaixaRepository.class);
        org.mockito.Mockito.when(repo.detalhe(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(new FluxoCaixaDTOs.Detalhe(0, 1, 0, 0, BigDecimal.ZERO, List.of()));
        FluxoCaixaExportacaoService svc = new FluxoCaixaExportacaoService(mock(FluxoCaixaService.class), repo);
        FluxoCaixaExportacaoService.Relatorio rel = svc.montar(new FluxoCaixaFiltro(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-01"), null, null, null, null, null, null), Visao.DIARIO, r,
                LocalDate.parse("2026-11-01"));
        String tudo = rel.secoes().stream().flatMap(sec -> java.util.stream.Stream.concat(sec.cabecalho().stream(), sec.linhas().stream().flatMap(l -> l.stream().map(String::valueOf))))
                .reduce("", (a, b) -> a + "|" + b).toLowerCase();
        assertFalse(tudo.contains("saldo"));
        assertFalse(rel.secoes().stream().anyMatch(sec -> sec.titulo().equals("Saldo por banco")));
    }

    @Test
    void cabecalhoTemNomesDosFiltrosVisaoComAcentoEFormatoMonetario() throws Exception {
        FluxoCaixaRepository repo = mock(FluxoCaixaRepository.class);
        org.mockito.Mockito.when(repo.nomesPorId("categoria_receita", List.of(3L))).thenReturn(List.of("Vendas"));
        org.mockito.Mockito.when(repo.nomesPorId("centro_custo", List.of(7L))).thenReturn(List.of("Loja Centro"));
        java.util.UUID cliente = java.util.UUID.randomUUID();
        org.mockito.Mockito.when(repo.nomeCliente(cliente)).thenReturn("CAMILA");
        FluxoCaixaDTOs.Resumo s = new FluxoCaixaDTOs.Resumo(null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, new FluxoCaixaDTOs.Atrasado(BigDecimal.ZERO, 0), new FluxoCaixaDTOs.Atrasado(BigDecimal.ZERO, 0), null);
        FluxoCaixaDTOs.Resposta r = new FluxoCaixaDTOs.Resposta(new FluxoCaixaDTOs.Periodo(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-01"), "DIARIO", LocalDate.parse("2026-11-01")),
                false, null, false, s, List.of(), null, new FluxoCaixaDTOs.Categorias(List.of(), List.of()), null);
        org.mockito.Mockito.when(repo.detalhe(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(new FluxoCaixaDTOs.Detalhe(0, 1, 0, 0, BigDecimal.ZERO, List.of()));
        FluxoCaixaExportacaoService svc = new FluxoCaixaExportacaoService(mock(FluxoCaixaService.class), repo);
        FluxoCaixaExportacaoService.Relatorio rel = svc.montar(new FluxoCaixaFiltro(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-01"), null, List.of(3L), null, List.of(7L), cliente, null),
                Visao.DIARIO, r, LocalDate.parse("2026-11-01"));
        assertTrue(rel.periodo().contains("Visão: diário"));
        assertTrue(rel.filtros().contains("Categorias de receita: Vendas"));
        assertTrue(rel.filtros().contains("Centros de custo: Loja Centro"));
        assertTrue(rel.filtros().contains("Cliente: CAMILA"));
        assertFalse(rel.filtros().contains("selecionad"));
        // Excel: valores monetários com formato R$
        byte[] xlsx = svc.xlsx(rel);
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            org.apache.poi.ss.usermodel.Sheet sh = wb.getSheetAt(0);
            String formato = null;
            for (org.apache.poi.ss.usermodel.Row row : sh) {
                org.apache.poi.ss.usermodel.Cell c = row.getCell(1);
                if (c != null && c.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                    formato = c.getCellStyle().getDataFormatString();
                    break;
                }
            }
            assertNotNull(formato);
            assertTrue(formato.contains("R$"));
        }
    }
}
