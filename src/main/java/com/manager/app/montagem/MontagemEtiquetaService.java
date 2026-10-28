package com.manager.app.montagem;

import com.manager.app.cliente.Cliente;
import com.manager.app.consignacao.Consignacao;
import com.manager.app.endereco.Endereco;
import com.manager.app.montagem.dto.MontagemEtiquetaDTO;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MontagemEtiquetaService {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String RELATORIO = "/reports/etiqueta/relEtiquetaMontagemMostruario.jasper";
    private static final String CABECALHO = "cabecalhoEtiquetaMontagem.png";

    private final MontagemMostruarioRepository repository;
    private final MontagemValidation validation;

    @Value("${app.storage.relatorio-path}")
    private String relatorioPath;

    @Transactional(readOnly = true)
    public byte[] gerarEtiqueta(Long montagemId){
        MontagemMostruario montagem = repository.findById(montagemId)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Montagem não encontrada. id: " + montagemId));
        validation.validarEtiqueta(montagem);
        MontagemEtiquetaDTO dados = montarDados(montagem);

        Path cabecalho = Path.of(relatorioPath, CABECALHO);
        try (InputStream jasperStream = getClass().getResourceAsStream(RELATORIO);
             InputStream cabecalhoStream = Files.exists(cabecalho) ? Files.newInputStream(cabecalho) : null) {
            Map<String, Object> params = new HashMap<>();
            // sem o arquivo no caminho configurado, a etiqueta sai sem o cabeçalho (igual aos relatórios de consignação)
            params.put("cabecalho", cabecalhoStream);
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(List.of(dados));
            JasperPrint print = JasperFillManager.fillReport(jasperStream, params, dataSource);
            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar etiqueta da montagem", e);
        }
    }

    MontagemEtiquetaDTO montarDados(MontagemMostruario montagem){
        Cliente cliente = montagem.getCliente();
        List<Endereco> enderecos = cliente != null ? cliente.getEnderecos() : List.of();
        Endereco principal = enderecoPrincipal(enderecos);
        String nome = cliente != null ? cliente.getNome() : null;
        Consignacao primeira = montagem.getConsignacoes().get(0);

        return MontagemEtiquetaDTO.builder()
                .codCliente(vazioSeNulo(cliente != null ? cliente.getCodigo() : null))
                .nomeContatoCliente(nomeContato(nome, cliente != null ? cliente.getCelular() : null, cliente != null ? cliente.getTelefone() : null))
                .mostruario(montagem.getMostruario() != null ? vazioSeNulo(montagem.getMostruario().getDescricao()) : "")
                .dataEntrega(formatarData(montagem.getDataEntrega()))
                .dataAcerto(formatarData(primeira.getDataAcertoConsignacao()))
                .refCliente(vazioSeNulo(cliente != null ? cliente.getReferencia() : null))
                .enderecos(enderecosEmLinhas(enderecos))
                .nomeBairroCidadeCliente(nomeBairroCidade(nome, principal))
                .sobrenomeCliente(sobrenome(nome))
                .cidadeCliente(principal != null ? vazioSeNulo(principal.getMunicipio()) : "")
                .build();
    }

    // "Maria Silva" -> "Silva"; 5+ palavras -> todas menos as 2 primeiras; 1 palavra -> ela mesma
    public static String sobrenome(String nomeCompleto){
        if (nomeCompleto == null || nomeCompleto.isBlank()) return "";
        String[] palavras = nomeCompleto.trim().split("\\s+");
        if (palavras.length == 1) return palavras[0];
        int ignorar = palavras.length > 4 ? 2 : 1;
        return String.join(" ", java.util.Arrays.copyOfRange(palavras, ignorar, palavras.length));
    }

    // "nome - celular / telefone", omitindo as partes vazias
    public static String nomeContato(String nome, String celular, String telefone){
        String contatos = juntar(" / ", celular, telefone);
        return juntar(" - ", nome, contatos);
    }

    // "nome - bairro - cidade" com bairro/município do endereço principal
    public static String nomeBairroCidade(String nome, Endereco principal){
        return juntar(" - ", nome, principal != null ? principal.getBairro() : null, principal != null ? principal.getMunicipio() : null);
    }

    // principal==true; se nenhum for principal, o primeiro; sem endereços, null
    public static Endereco enderecoPrincipal(List<Endereco> enderecos){
        if (enderecos == null || enderecos.isEmpty()) return null;
        return enderecos.stream().filter(Endereco::isPrincipal).findFirst().orElse(enderecos.get(0));
    }

    // todos os endereços, um por linha: principal primeiro, depois os demais por id
    public static String enderecosEmLinhas(List<Endereco> enderecos){
        if (enderecos == null || enderecos.isEmpty()) return "";
        List<Endereco> ordenados = new ArrayList<>(enderecos);
        ordenados.sort(Comparator.comparing((Endereco e) -> !e.isPrincipal())
                .thenComparing(Endereco::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return ordenados.stream().map(MontagemEtiquetaService::enderecoEmLinha).filter(l -> !l.isEmpty()).collect(Collectors.joining("\n"));
    }

    // "descricao: logradouro - numero - bairro - municipio - complemento - pontoReferencia (latitude, longitude)"
    public static String enderecoEmLinha(Endereco e){
        String corpo = juntar(" - ", e.getLogradouro(), e.getNumero(), e.getBairro(), e.getMunicipio(), e.getComplemento(), e.getPontoReferencia());
        if (!vazio(e.getDescricao())) {
            corpo = corpo.isEmpty() ? e.getDescricao().trim() + ":" : e.getDescricao().trim() + ": " + corpo;
        }
        BigDecimal lat = e.getLatitude();
        BigDecimal lng = e.getLongitude();
        if (lat != null && lng != null) {
            String coordenadas = "(" + lat.toPlainString() + ", " + lng.toPlainString() + ")";
            corpo = corpo.isEmpty() ? coordenadas : corpo + " " + coordenadas;
        }
        return corpo;
    }

    public static String formatarData(LocalDate data){
        return data != null ? data.format(FORMATO_DATA) : "";
    }

    private static String juntar(String separador, String... partes){
        return java.util.Arrays.stream(partes).filter(p -> !vazio(p)).map(String::trim).collect(Collectors.joining(separador));
    }

    private static boolean vazio(String valor){
        return valor == null || valor.isBlank();
    }

    private static String vazioSeNulo(String valor){
        return Objects.requireNonNullElse(valor, "");
    }
}
