package com.manager.app.fluxocaixa;

import com.manager.app.fluxocaixa.FluxoCaixaCalculo.Visao;
import com.manager.app.fluxocaixa.FluxoCaixaRepository.Lado;
import com.manager.app.fluxocaixa.dto.FluxoCaixaDTOs;
import com.manager.app.shared.exception.ItemInvalidoException;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

// exportação do fluxo de caixa em Excel (Apache POI) e PDF (JasperReports, modelo genérico em colunas): cartões, série, categorias e detalhe.
// Sem permissão de saldo, os campos de saldo simplesmente não existem no arquivo (vêm nulos do serviço).
@Service
@RequiredArgsConstructor
public class FluxoCaixaExportacaoService {

    public static final int LIMITE_DETALHE = 5000;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final FluxoCaixaService service;
    private final FluxoCaixaRepository repository;

    public record Arquivo(String nome, String tipo, byte[] conteudo) {
    }

    // seção neutra: cabeçalho e linhas com valores String, BigDecimal ou LocalDate
    record Secao(String titulo, List<String> cabecalho, List<List<Object>> linhas, String nota) {
    }

    record Relatorio(String titulo, String periodo, String filtros, String geradoEm, List<Secao> secoes) {
    }

    @Transactional(readOnly = true)
    public Arquivo exportar(FluxoCaixaFiltro filtroBruto, Visao visao, String formato) {
        String f = formato == null ? "" : formato.trim().toLowerCase(Locale.ROOT);
        if (!f.equals("xlsx") && !f.equals("pdf")) throw new ItemInvalidoException("Formato inválido. Use xlsx ou pdf.");
        LocalDate hoje = LocalDate.now();
        FluxoCaixaFiltro filtro = FluxoCaixaService.normalizar(filtroBruto, hoje);
        FluxoCaixaDTOs.Resposta r = service.resumo(filtro, visao);
        Relatorio relatorio = montar(filtro, visao, r, hoje);
        String base = "fluxo-de-caixa_" + filtro.inicio() + "_" + filtro.fim();
        if (f.equals("xlsx")) return new Arquivo(base + ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx(relatorio));
        return new Arquivo(base + ".pdf", "application/pdf", pdf(relatorio));
    }

    // ---------- modelo ----------

    Relatorio montar(FluxoCaixaFiltro filtro, Visao visao, FluxoCaixaDTOs.Resposta r, LocalDate hoje) {
        List<Secao> secoes = new ArrayList<>();
        FluxoCaixaDTOs.Resumo s = r.resumo();
        List<List<Object>> cartoes = new ArrayList<>();
        if (r.saldosVisiveis()) cartoes.add(linha("Saldo inicial", s.saldoInicial()));
        cartoes.add(linha("Entradas realizadas", s.entradasRealizadas()));
        cartoes.add(linha("Entradas previstas", s.entradasPrevistas()));
        cartoes.add(linha("Saídas realizadas", s.saidasRealizadas()));
        cartoes.add(linha("Saídas previstas", s.saidasPrevistas()));
        cartoes.add(linha("Resultado realizado", s.resultadoRealizado()));
        cartoes.add(linha("Resultado previsto", s.resultadoPrevisto()));
        if (r.saldosVisiveis()) {
            cartoes.add(linha("Outras movimentações (transferências e ajustes)", s.outrasMovimentacoes()));
            cartoes.add(linha("Saldo final realizado", s.saldoFinalRealizado()));
            cartoes.add(linha("Saldo final projetado", s.saldoFinalProjetado()));
        }
        cartoes.add(linha("Atrasados a receber (valor)", s.atrasadosReceber().valor()));
        cartoes.add(linha("Atrasados a receber (títulos)", String.valueOf(s.atrasadosReceber().quantidade())));
        cartoes.add(linha("Atrasados a pagar (valor)", s.atrasadosPagar().valor()));
        cartoes.add(linha("Atrasados a pagar (títulos)", String.valueOf(s.atrasadosPagar().quantidade())));
        secoes.add(new Secao("Resumo", List.of("Indicador", "Valor"), cartoes, r.previstoSemBanco() ? "O previsto não possui banco: não é filtrado pelos bancos selecionados." : null));

        List<String> cab = new ArrayList<>(List.of("Período", "Entradas realizadas", "Entradas previstas", "Saídas realizadas", "Saídas previstas", "Atrasados a receber", "Atrasados a pagar"));
        if (r.saldosVisiveis()) {
            cab.add("Saldo realizado");
            cab.add("Saldo projetado");
        }
        List<List<Object>> serie = new ArrayList<>();
        for (FluxoCaixaDTOs.Ponto p : r.serie()) {
            List<Object> l = new ArrayList<>(List.of(p.rotulo(), p.entradasRealizadas(), p.entradasPrevistas(), p.saidasRealizadas(), p.saidasPrevistas(), p.atrasadosReceber(), p.atrasadosPagar()));
            if (r.saldosVisiveis()) {
                l.add(p.saldoAcumuladoRealizado());
                l.add(p.saldoAcumuladoProjetado());
            }
            serie.add(l);
        }
        secoes.add(new Secao("Série por período", cab, serie, null));

        if (r.porBanco() != null && !r.porBanco().isEmpty()) {
            List<List<Object>> bancos = r.porBanco().stream().<List<Object>>map(b -> new ArrayList<>(List.of(b.nome(), b.saldoInicial(), b.entradasRealizadas(), b.saidasRealizadas(),
                    b.outrasMovimentacoes(), b.saldoFinalRealizado()))).collect(Collectors.toList());
            secoes.add(new Secao("Saldo por banco", List.of("Banco", "Saldo inicial", "Entradas realizadas", "Saídas realizadas", "Outras movimentações", "Saldo final"), bancos, null));
        }
        secoes.add(categorias("Receitas por categoria", r.porCategoria().receitas()));
        secoes.add(categorias("Despesas por categoria", r.porCategoria().despesas()));
        secoes.add(detalhe(filtro, hoje));

        String periodo = "Período: " + filtro.inicio().format(DATA) + " a " + filtro.fim().format(DATA) + " | Visão: " + visao.name().toLowerCase(Locale.ROOT);
        return new Relatorio("Fluxo de Caixa", periodo, descricaoDosFiltros(filtro), "Gerado em " + LocalDateTime.now().format(DATA_HORA), secoes);
    }

    private static Secao categorias(String titulo, List<FluxoCaixaDTOs.PorCategoria> lista) {
        List<List<Object>> linhas = new ArrayList<>();
        for (FluxoCaixaDTOs.PorCategoria c : lista) {
            linhas.add(new ArrayList<>(List.of(c.pai() == null ? c.nome() : c.pai() + " > " + c.nome(), c.realizado(), c.previsto(), c.atrasado(), c.percentualDoTotal() + "%")));
        }
        return new Secao(titulo, List.of("Categoria", "Realizado", "Previsto", "Atrasado", "% do total"), linhas, null);
    }

    private Secao detalhe(FluxoCaixaFiltro filtro, LocalDate hoje) {
        List<List<Object>> linhas = new ArrayList<>();
        long total = 0;
        LocalDate previstoDe = filtro.inicio().isAfter(hoje) ? filtro.inicio() : hoje;
        for (Lado lado : Lado.values()) {
            if (lado == Lado.RECEBER && !filtro.incluiReceber()) continue;
            for (String situacao : List.of("REALIZADO", "PREVISTO", "ATRASADO")) {
                LocalDate de = situacao.equals("PREVISTO") ? previstoDe : filtro.inicio();
                if (de.isAfter(filtro.fim())) continue;
                int restante = Math.max(1, LIMITE_DETALHE - linhas.size());
                FluxoCaixaDTOs.Detalhe d = repository.detalhe(lado, situacao, filtro, de, filtro.fim(), hoje, "data", situacao.equals("REALIZADO"), 0, restante);
                total += d.totalElementos();
                if (linhas.size() >= LIMITE_DETALHE) continue;
                for (FluxoCaixaDTOs.Item i : d.itens()) {
                    List<Object> l = new ArrayList<>();
                    l.add(i.tipo().equals("ENTRADA") ? "Entrada" : "Saída");
                    l.add(rotuloSituacao(i.situacao()));
                    l.add(String.valueOf(i.titulo().numero()));
                    l.add(i.pessoa() == null ? "" : i.pessoa());
                    l.add(i.categoria() == null ? "" : i.categoria());
                    l.add(i.banco() == null ? "" : i.banco());
                    l.add(i.data());
                    l.add(i.valor());
                    linhas.add(l);
                }
            }
        }
        String nota = total > linhas.size() ? "O detalhe foi limitado a " + LIMITE_DETALHE + " linhas de um total de " + total + ". Refine os filtros para ver todos." : null;
        return new Secao("Detalhe", List.of("Tipo", "Situação", "Título nº", "Pessoa", "Categoria", "Banco", "Data", "Valor"), linhas, nota);
    }

    private static String rotuloSituacao(String s) {
        return switch (s) {
            case "REALIZADO" -> "Realizado";
            case "PREVISTO" -> "Previsto";
            default -> "Atrasado";
        };
    }

    private static List<Object> linha(Object... v) {
        return new ArrayList<>(List.of(v));
    }

    private String descricaoDosFiltros(FluxoCaixaFiltro f) {
        List<String> partes = new ArrayList<>();
        if (!f.bancoIds().isEmpty()) {
            partes.add("Bancos: " + repository.bancos(f.bancoIds()).stream().map(FluxoCaixaRepository.BancoLinha::nome).collect(Collectors.joining(", ")));
        }
        if (!f.categoriaReceitaIds().isEmpty()) partes.add("Categorias de receita: " + f.categoriaReceitaIds().size() + " selecionada(s)");
        if (!f.categoriaDespesaIds().isEmpty()) partes.add("Categorias de despesa: " + f.categoriaDespesaIds().size() + " selecionada(s)");
        if (!f.centroCustoIds().isEmpty()) partes.add("Centros de custo: " + f.centroCustoIds().size() + " selecionado(s)");
        if (f.clienteId() != null) partes.add("Cliente selecionado");
        if (f.favorecido() != null) partes.add("Favorecido: " + f.favorecido());
        return partes.isEmpty() ? "Filtros: nenhum" : "Filtros: " + String.join(" | ", partes);
    }

    // ---------- formatação ----------

    static String texto(Object v) {
        if (v == null) return "";
        if (v instanceof BigDecimal b) return NumberFormat.getCurrencyInstance(PT_BR).format(b).replace(' ', ' ');
        if (v instanceof LocalDate d) return d.format(DATA);
        return String.valueOf(v);
    }

    // ---------- Excel ----------

    byte[] xlsx(Relatorio rel) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font negrito = wb.createFont();
            negrito.setBold(true);
            CellStyle cab = wb.createCellStyle();
            cab.setFont(negrito);
            cab.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            cab.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            DataFormat fmt = wb.createDataFormat();
            CellStyle moeda = wb.createCellStyle();
            moeda.setDataFormat(fmt.getFormat("#,##0.00"));
            CellStyle data = wb.createCellStyle();
            data.setDataFormat(fmt.getFormat("dd/mm/yyyy"));
            CellStyle titulo = wb.createCellStyle();
            Font grande = wb.createFont();
            grande.setBold(true);
            grande.setFontHeightInPoints((short) 14);
            titulo.setFont(grande);
            for (Secao s : rel.secoes()) {
                Sheet sh = wb.createSheet(nomeAba(s.titulo()));
                int n = 0;
                Row t = sh.createRow(n++);
                Cell ct = t.createCell(0);
                ct.setCellValue(rel.titulo() + " - " + s.titulo());
                ct.setCellStyle(titulo);
                sh.createRow(n++).createCell(0).setCellValue(rel.periodo());
                sh.createRow(n++).createCell(0).setCellValue(rel.filtros());
                sh.createRow(n++).createCell(0).setCellValue(rel.geradoEm());
                if (s.nota() != null) sh.createRow(n++).createCell(0).setCellValue(s.nota());
                n++;
                Row h = sh.createRow(n++);
                for (int c = 0; c < s.cabecalho().size(); c++) {
                    Cell cell = h.createCell(c);
                    cell.setCellValue(s.cabecalho().get(c));
                    cell.setCellStyle(cab);
                }
                for (List<Object> l : s.linhas()) {
                    Row row = sh.createRow(n++);
                    for (int c = 0; c < l.size(); c++) {
                        Cell cell = row.createCell(c);
                        Object v = l.get(c);
                        if (v instanceof BigDecimal b) {
                            cell.setCellValue(b.doubleValue());
                            cell.setCellStyle(moeda);
                        } else if (v instanceof LocalDate d) {
                            cell.setCellValue(java.sql.Date.valueOf(d));
                            cell.setCellStyle(data);
                        } else {
                            cell.setCellValue(v == null ? "" : String.valueOf(v));
                        }
                    }
                }
                for (int c = 0; c < s.cabecalho().size(); c++) sh.setColumnWidth(c, c == 0 ? 9000 : 4500);
                sh.createFreezePane(0, s.nota() != null ? 7 : 6);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar o arquivo Excel do fluxo de caixa", e);
        }
    }

    private static String nomeAba(String titulo) {
        return titulo.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
    }

    // ---------- PDF ----------

    private static volatile JasperReport modelo;

    private static JasperReport modelo() {
        if (modelo == null) {
            synchronized (FluxoCaixaExportacaoService.class) {
                if (modelo == null) {
                    try (InputStream in = FluxoCaixaExportacaoService.class.getResourceAsStream("/reports/fluxo/fluxo-caixa.jrxml")) {
                        modelo = JasperCompileManager.compileReport(in);
                    } catch (Exception e) {
                        throw new RuntimeException("Erro ao preparar o modelo do relatório de fluxo de caixa", e);
                    }
                }
            }
        }
        return modelo;
    }

    byte[] pdf(Relatorio rel) {
        try {
            List<Map<String, ?>> linhas = new ArrayList<>();
            for (Secao s : rel.secoes()) {
                linhas.add(linhaPdf("T", List.of(s.titulo())));
                if (s.nota() != null) linhas.add(linhaPdf("N", List.of(s.nota())));
                linhas.add(linhaPdf("H", s.cabecalho()));
                for (List<Object> l : s.linhas()) linhas.add(linhaPdf("L", l.stream().map(FluxoCaixaExportacaoService::texto).collect(Collectors.toList())));
                linhas.add(linhaPdf("L", List.of("")));
            }
            Map<String, Object> params = new HashMap<>();
            params.put("titulo", rel.titulo());
            params.put("periodo", rel.periodo());
            params.put("filtros", rel.filtros());
            params.put("geradoEm", rel.geradoEm());
            JasperPrint print = JasperFillManager.fillReport(modelo(), params, new JRMapCollectionDataSource((Collection<Map<String, ?>>) (Collection<?>) linhas));
            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar o PDF do fluxo de caixa", e);
        }
    }

    private static Map<String, ?> linhaPdf(String tipo, List<String> colunas) {
        Map<String, Object> m = new HashMap<>();
        m.put("tipoLinha", tipo);
        for (int i = 1; i <= 9; i++) m.put("c" + i, i <= colunas.size() ? colunas.get(i - 1) : "");
        return m;
    }
}
