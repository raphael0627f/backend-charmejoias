package com.manager.app.planejadorrota;

import com.manager.app.cliente.Cliente;
import com.manager.app.endereco.Endereco;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// monta o .xlsx de uma PlanejadorRota no mesmo layout usado hoje manualmente pela equipe de campo
@Service
@RequiredArgsConstructor
public class PlanejadorRotaXlsxService {

    private final PlanejadorRotaDetalhes detalhes;

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String[] CABECALHO = {
            "ITEM", "codigo", "VISITA", "ROTA", "IDA", "VOLTA", "DEIXOU", "TROUXE",
            "nome do cliente", "Endereço", "Latitude", "Longitude", "Notas", "Cliente Móvel", "Referência", "observação",
            "Valor a pagar", "Deixar (documentos)", "Trazer (documentos)"
    };

    public byte[] gerarXlsx(PlanejadorRota rota){
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(rota.getNome() != null ? rota.getNome() : "Rota");

            CellStyle cabecalhoStyle = criarCabecalhoStyle(workbook);
            CellStyle enderecoStyle = criarEnderecoStyle(workbook);

            Row linhaCabecalho = sheet.createRow(0);
            for (int i = 0; i < CABECALHO.length; i++) {
                Cell cell = linhaCabecalho.createCell(i);
                cell.setCellValue(CABECALHO[i]);
                cell.setCellStyle(cabecalhoStyle);
            }

            List<PlanejadorRotaCliente> itens = rota.getItens().stream()
                    .sorted(Comparator.comparing(PlanejadorRotaCliente::getOrdem, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();

            Map<Long, Long> montagens = detalhes.montagemPorConsignacao(rota);
            int linhaIndex = 1;
            for (PlanejadorRotaCliente item : itens) {
                Row row = sheet.createRow(linhaIndex++);
                preencherLinha(row, rota, item, enderecoStyle, montagens);
            }

            for (int i = 0; i < CABECALHO.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar o .xlsx da rota do planejador. Id: " + rota.getId(), e);
        }
    }

    private void preencherLinha(Row row, PlanejadorRota rota, PlanejadorRotaCliente item, CellStyle enderecoStyle, Map<Long, Long> montagens){
        Cliente cliente = item.getCliente();
        Endereco enderecoSelecionado = item.getEndereco();
        // IDA/VOLTA = previsto (quantidade de consignacoes planejadas pra deixar/trazer); DEIXOU/TROUXE =
        // resultado real (quantas dessas ja foram confirmadas), preenchido na edicao pos-rota
        // (o que se deixa pode ser consignacao e/ou pedido SEPARADO; os dois entram na conta do deixar)
        int previstoDeixar = item.getConsignacoesDeixar().size() + item.getPedidosDeixar().size();
        int previstoTrazer = item.getConsignacoesTrazer().size();
        long confirmadoDeixar = item.getConsignacoesDeixar().stream().filter(c -> Boolean.TRUE.equals(c.getConfirmada())).count()
                + item.getPedidosDeixar().stream().filter(p -> Boolean.TRUE.equals(p.getConfirmada())).count();
        long confirmadoTrazer = item.getConsignacoesTrazer().stream().filter(c -> Boolean.TRUE.equals(c.getConfirmada())).count();

        row.createCell(0).setCellValue(item.getOrdem() != null ? item.getOrdem() : row.getRowNum());
        row.createCell(1).setCellValue(cliente.getCodigo());
        row.createCell(2).setCellValue(rota.getDataRota() != null ? rota.getDataRota().format(FORMATO_DATA) : "");
        row.createCell(3).setCellValue(rota.getNome());
        row.createCell(4).setCellValue(previstoDeixar > 0 ? 1 : 0);
        row.createCell(5).setCellValue(previstoTrazer > 0 ? 1 : 0);
        row.createCell(6).setCellValue(confirmadoDeixar);
        row.createCell(7).setCellValue(confirmadoTrazer);
        row.createCell(8).setCellValue(cliente.getNome());

        Cell enderecoCell = row.createCell(9);
        enderecoCell.setCellValue(enderecosDoCliente(cliente, enderecoSelecionado));
        enderecoCell.setCellStyle(enderecoStyle);

        row.createCell(10).setCellValue(enderecoSelecionado.getLatitude() != null ? enderecoSelecionado.getLatitude().toPlainString() : "");
        row.createCell(11).setCellValue(enderecoSelecionado.getLongitude() != null ? enderecoSelecionado.getLongitude().toPlainString() : "");
        row.createCell(12).setCellValue(enderecoSelecionado.getDescricao() != null ? enderecoSelecionado.getDescricao() : "");
        row.createCell(13).setCellValue(cliente.getCelular() != null ? cliente.getCelular() : "");
        // coluna antes chamada COR: agora traz a referência do cliente
        row.createCell(14).setCellValue(cliente.getReferencia() != null ? cliente.getReferencia() : "");
        row.createCell(15).setCellValue(item.getObservacao() != null ? item.getObservacao() : "");
        row.createCell(16).setCellValue(item.getValorAPagar() != null ? item.getValorAPagar().doubleValue() : 0);

        // quais documentos, um por linha na celula: "Consignação Nº 8756 (Montagem Nº 830)" / "Pedido Nº 12"
        Cell deixarCell = row.createCell(17);
        deixarCell.setCellValue(documentosDeixar(item, montagens));
        deixarCell.setCellStyle(enderecoStyle);
        Cell trazerCell = row.createCell(18);
        trazerCell.setCellValue(item.getConsignacoesTrazer().stream()
                .map(c -> descricaoConsignacao(c.getConsignacao().getId(), montagens))
                .collect(Collectors.joining("\n")));
        trazerCell.setCellStyle(enderecoStyle);
    }

    private String documentosDeixar(PlanejadorRotaCliente item, Map<Long, Long> montagens){
        return Stream.concat(
                        item.getConsignacoesDeixar().stream().map(c -> descricaoConsignacao(c.getConsignacao().getId(), montagens)),
                        item.getPedidosDeixar().stream().map(p -> "Pedido Nº " + p.getPedido().getId()))
                .collect(Collectors.joining("\n"));
    }

    static String descricaoConsignacao(Long consignacaoId, Map<Long, Long> montagens){
        Long montagemId = montagens.get(consignacaoId);
        return "Consignação Nº " + consignacaoId + (montagemId != null ? " (Montagem Nº " + montagemId + ")" : "");
    }

    // todos os enderecos cadastrados do cliente, comecando pelo selecionado, um por linha na mesma celula
    private String enderecosDoCliente(Cliente cliente, Endereco selecionado){
        return Stream.concat(
                        Stream.of(selecionado),
                        cliente.getEnderecos().stream().filter(e -> !e.getId().equals(selecionado.getId())))
                .map(PlanejadorRotaXlsxService::formatarEndereco)
                .collect(Collectors.joining("\n"));
    }

    // "DESCRIÇÃO: logradouro, número - ..." (sem descrição, sem prefixo nem dois-pontos)
    static String formatarEndereco(Endereco endereco){
        StringBuilder sb = new StringBuilder();
        if (endereco.getDescricao() != null && !endereco.getDescricao().isBlank()) {
            sb.append(endereco.getDescricao().trim()).append(": ");
        }
        sb.append(endereco.getLogradouro() != null ? endereco.getLogradouro() : "");
        if (endereco.getNumero() != null && !endereco.getNumero().isBlank()) {
            sb.append(", ").append(endereco.getNumero());
        }
        if (endereco.getComplemento() != null && !endereco.getComplemento().isBlank()) {
            sb.append(" - ").append(endereco.getComplemento());
        }
        if (endereco.getPontoReferencia() != null && !endereco.getPontoReferencia().isBlank()) {
            sb.append(" (").append(endereco.getPontoReferencia()).append(")");
        }
        if (endereco.getBairro() != null && !endereco.getBairro().isBlank()) {
            sb.append(" - ").append(endereco.getBairro());
        }
        if (endereco.getMunicipio() != null && !endereco.getMunicipio().isBlank()) {
            sb.append(" - ").append(endereco.getMunicipio());
            if (endereco.getUf() != null && !endereco.getUf().isBlank()) {
                sb.append("/").append(endereco.getUf());
            }
        }
        return sb.toString();
    }

    private CellStyle criarCabecalhoStyle(Workbook workbook){
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private CellStyle criarEnderecoStyle(Workbook workbook){
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        return style;
    }
}
