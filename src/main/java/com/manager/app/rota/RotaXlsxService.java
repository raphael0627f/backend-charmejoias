package com.manager.app.rota;

import com.manager.app.cliente.Cliente;
import com.manager.app.endereco.Endereco;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// monta o .xlsx de uma Rota no mesmo layout usado hoje manualmente pela equipe de campo
@Service
public class RotaXlsxService {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String[] CABECALHO = {
            "ITEM", "codigo", "VISITA", "ROTA", "IDA", "VOLTA", "DEIXOU", "TROUXE",
            "nome do cliente", "Endereço", "Latitude", "Longitude", "Notas", "Cliente Móvel", "COR", "observação"
    };

    public byte[] gerarXlsx(Rota rota){
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

            List<RotaCliente> itens = rota.getItens().stream()
                    .sorted(Comparator.comparing(RotaCliente::getOrdem, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();

            int linhaIndex = 1;
            for (RotaCliente item : itens) {
                Row row = sheet.createRow(linhaIndex++);
                preencherLinha(row, rota, item, enderecoStyle);
            }

            for (int i = 0; i < CABECALHO.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar o .xlsx da Rota. id: " + rota.getId(), e);
        }
    }

    private void preencherLinha(Row row, Rota rota, RotaCliente item, CellStyle enderecoStyle){
        Cliente cliente = item.getCliente();
        Endereco enderecoSelecionado = item.getEndereco();
        // IDA/VOLTA = previsto (existe consignacao planejada pra deixar/trazer); DEIXOU/TROUXE =
        // resultado real preenchido depois, na edicao pos-rota (null enquanto ainda nao foi a campo)
        boolean previstoDeixar = item.getConsignacaoDeixar() != null;
        boolean previstoTrazer = item.getConsignacaoTrazer() != null;

        row.createCell(0).setCellValue(item.getOrdem() != null ? item.getOrdem() : row.getRowNum());
        row.createCell(1).setCellValue(cliente.getCodigo());
        row.createCell(2).setCellValue(rota.getDataRota() != null ? rota.getDataRota().format(FORMATO_DATA) : "");
        row.createCell(3).setCellValue(rota.getNome());
        row.createCell(4).setCellValue(previstoDeixar ? 1 : 0);
        row.createCell(5).setCellValue(previstoTrazer ? 1 : 0);
        row.createCell(6).setCellValue(Boolean.TRUE.equals(item.getDeixouReal()) ? 1 : 0);
        row.createCell(7).setCellValue(Boolean.TRUE.equals(item.getTrouxeReal()) ? 1 : 0);
        row.createCell(8).setCellValue(cliente.getNome());

        Cell enderecoCell = row.createCell(9);
        enderecoCell.setCellValue(enderecosDoCliente(cliente, enderecoSelecionado));
        enderecoCell.setCellStyle(enderecoStyle);

        row.createCell(10).setCellValue(enderecoSelecionado.getLatitude() != null ? enderecoSelecionado.getLatitude().toPlainString() : "");
        row.createCell(11).setCellValue(enderecoSelecionado.getLongitude() != null ? enderecoSelecionado.getLongitude().toPlainString() : "");
        row.createCell(12).setCellValue(enderecoSelecionado.getDescricao() != null ? enderecoSelecionado.getDescricao() : "");
        row.createCell(13).setCellValue(cliente.getCelular() != null ? cliente.getCelular() : "");
        row.createCell(14).setCellValue("");
        row.createCell(15).setCellValue(item.getObservacao() != null ? item.getObservacao() : "");
    }

    // todos os enderecos cadastrados do cliente, comecando pelo selecionado, um por linha na mesma celula
    private String enderecosDoCliente(Cliente cliente, Endereco selecionado){
        return Stream.concat(
                        Stream.of(selecionado),
                        cliente.getEnderecos().stream().filter(e -> !e.getId().equals(selecionado.getId())))
                .map(this::formatarEndereco)
                .collect(Collectors.joining("\n"));
    }

    private String formatarEndereco(Endereco endereco){
        StringBuilder sb = new StringBuilder();
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
