package com.manager.app.planejadorrota;

import com.manager.app.cliente.Cliente;
import com.manager.app.endereco.Endereco;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanejadorRotaXlsxServiceTest {

    private final PlanejadorRotaXlsxService service = new PlanejadorRotaXlsxService(org.mockito.Mockito.mock(PlanejadorRotaDetalhes.class));

    private Endereco endereco(Long id, String descricao, String logradouro) {
        Endereco e = new Endereco();
        e.setId(id);
        e.setDescricao(descricao);
        e.setLogradouro(logradouro);
        e.setNumero("10");
        e.setBairro("Centro");
        e.setMunicipio("Campinas");
        e.setUf("SP");
        return e;
    }

    private Cliente cliente(String referencia, Endereco... enderecos) {
        Cliente c = new Cliente();
        c.setId(UUID.randomUUID());
        c.setCodigo("C1");
        c.setNome("Maria");
        c.setReferencia(referencia);
        c.setEnderecos(new ArrayList<>(List.of(enderecos)));
        return c;
    }

    private Sheet gerar(Cliente cliente, Endereco selecionado) throws Exception {
        PlanejadorRota rota = new PlanejadorRota();
        rota.setId(1L);
        rota.setNome("Rota X");
        rota.setDataRota(LocalDate.of(2026, 11, 11));
        PlanejadorRotaCliente item = new PlanejadorRotaCliente();
        item.setOrdem(1);
        item.setCliente(cliente);
        item.setEndereco(selecionado);
        rota.setItens(new ArrayList<>(List.of(item)));
        Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(service.gerarXlsx(rota)));
        return wb.getSheetAt(0);
    }

    @Test
    void cabecalhoReferenciaNoIndice14ENotasInalterada() throws Exception {
        Endereco e = endereco(1L, "CASA", "Rua X");
        Row cab = gerar(cliente("perto da praça", e), e).getRow(0);
        assertThat(cab.getCell(14).getStringCellValue()).isEqualTo("Referência");
        assertThat(cab.getCell(12).getStringCellValue()).isEqualTo("Notas");
        assertThat(cab.getCell(9).getStringCellValue()).isEqualTo("Endereço");
        assertThat(cab.getLastCellNum()).isEqualTo((short) 19);
    }

    @Test
    void referenciaDoClienteVaiNaColuna() throws Exception {
        Endereco e = endereco(1L, "CASA", "Rua X");
        Row linha = gerar(cliente("perto da praça", e), e).getRow(1);
        assertThat(linha.getCell(14).getStringCellValue()).isEqualTo("perto da praça");
        // Notas segue com a descrição do endereço selecionado
        assertThat(linha.getCell(12).getStringCellValue()).isEqualTo("CASA");
    }

    @Test
    void referenciaNulaFicaVazia() throws Exception {
        Endereco e = endereco(1L, "CASA", "Rua X");
        Row linha = gerar(cliente(null, e), e).getRow(1);
        assertThat(linha.getCell(14).getStringCellValue()).isEmpty();
    }

    @Test
    void enderecoComDescricaoTemPrefixo() throws Exception {
        Endereco e = endereco(1L, "CASA", "Rua X");
        Row linha = gerar(cliente("r", e), e).getRow(1);
        assertThat(linha.getCell(9).getStringCellValue()).isEqualTo("CASA: Rua X, 10 - Centro - Campinas/SP");
    }

    @Test
    void enderecoSemDescricaoNaoTemPrefixoNemDoisPontos() {
        assertThat(PlanejadorRotaXlsxService.formatarEndereco(endereco(1L, null, "Rua X")))
                .isEqualTo("Rua X, 10 - Centro - Campinas/SP");
        assertThat(PlanejadorRotaXlsxService.formatarEndereco(endereco(1L, "   ", "Rua X")))
                .isEqualTo("Rua X, 10 - Centro - Campinas/SP");
    }

    @Test
    void variosEnderecosSelecionadoPrimeiroUmPorLinha() throws Exception {
        Endereco casa = endereco(1L, "CASA", "Rua A");
        Endereco trabalho = endereco(2L, "TRABALHO", "Rua B");
        Endereco semDescricao = endereco(3L, null, "Rua C");
        Row linha = gerar(cliente("r", casa, trabalho, semDescricao), trabalho).getRow(1);
        assertThat(linha.getCell(9).getStringCellValue().split("\n")).containsExactly(
                "TRABALHO: Rua B, 10 - Centro - Campinas/SP",
                "CASA: Rua A, 10 - Centro - Campinas/SP",
                "Rua C, 10 - Centro - Campinas/SP");
    }
}
