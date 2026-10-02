package com.manager.app.pedido;

import com.manager.app.grupo.Grupo;
import com.manager.app.grupo.TipoGrupo;
import com.manager.app.pedido.dto.PedidoFinalLinhaDTO;
import com.manager.app.pedido.dto.PedidoGrupoLinhaDTO;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// os .jasper foram compilados com o JasperReports 6.21.3 (mesma versão do pom) a partir dos .jrxml
// de resources/reports/pedido - ao alterar um .jrxml, recompilar com essa mesma versão
@Service
@RequiredArgsConstructor
public class PedidoRelatorioService {

    private static final String SEM_GRUPO = "Sem grupo";
    private static final String LOGO = "/images/logo_charme.png";
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final PedidoRepository repository;

    public enum OrdemSeparacao { CLIENTE, PRODUTO }

    // um único PDF: relPedidoFinal (uma linha por cliente com os totais) seguido do relPedidoGrupo
    // (itens por grupo, uma página por grupo), ambos sobre os pedidos selecionados pelo usuário
    @Transactional
    public byte[] gerarGrupoEFinal(List<Long> ids, OrdemSeparacao ordem, boolean marcarImpresso){
        List<Pedido> pedidos = carregar(ids);

        JasperPrint grupo = preencher("/reports/pedido/relPedidoGrupo.jasper", montarLinhasGrupo(pedidos, ordem));
        JasperPrint resumoFinal = preencher("/reports/pedido/relPedidoFinal.jasper", montarLinhasFinal(pedidos));

        byte[] pdf = exportar(List.of(resumoFinal, grupo));
        if (marcarImpresso) marcarComoImpresso(pedidos);
        return pdf;
    }

    private List<PedidoGrupoLinhaDTO> montarLinhasGrupo(List<Pedido> pedidos, OrdemSeparacao ordem){
        List<PedidoGrupoLinhaDTO> linhas = new ArrayList<>();
        for (Pedido pedido : pedidos) {
            for (PedidoProduto item : pedido.getProdutos()) {
                List<Grupo> grupos = gruposDe(item);
                // item com mais de um grupo aparece uma vez só, sob o nome dos grupos juntos (igual à tela)
                String idGrupo = grupos.isEmpty() ? "0" : String.join("-", grupos.stream().map(g -> String.valueOf(g.getId())).toList());
                String nomeGrupo = grupos.isEmpty() ? SEM_GRUPO : String.join(" / ", grupos.stream().map(Grupo::getNome).toList());

                linhas.add(new PedidoGrupoLinhaDTO(
                        codigoClienteDe(pedido), nomeClienteDe(pedido), cidadeDe(pedido),
                        codigoDe(item), nomeDe(item),
                        item.getQuantidade() != null ? item.getQuantidade() : 0,
                        moeda(item.getPreco()), variacaoDe(item),
                        idGrupo, nomeGrupo,
                        data(pedido.getDataEntrega()), data(pedido.getCreatedAt())));
            }
        }

        // o relatório quebra a página a cada grupo (ele precisa vir primeiro na ordenação); itens sem grupo por último
        Comparator<PedidoGrupoLinhaDTO> porGrupo = Comparator
                .comparing((PedidoGrupoLinhaDTO l) -> SEM_GRUPO.equals(l.getNomeGrupo()))
                .thenComparing(PedidoGrupoLinhaDTO::getNomeGrupo, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PedidoGrupoLinhaDTO::getIdGrupo);
        Comparator<PedidoGrupoLinhaDTO> comparador = ordem == OrdemSeparacao.PRODUTO
                ? porGrupo.thenComparing(PedidoGrupoLinhaDTO::getSkuProduto, String.CASE_INSENSITIVE_ORDER)
                : porGrupo.thenComparing(PedidoGrupoLinhaDTO::getNomeCliente, String.CASE_INSENSITIVE_ORDER)
                          .thenComparing(PedidoGrupoLinhaDTO::getSkuProduto, String.CASE_INSENSITIVE_ORDER);
        linhas.sort(comparador);
        return linhas;
    }

    // uma linha por cliente somando todos os pedidos selecionados dele. Os totais contam tudo; as
    // colunas de cor só somam itens cujo grupo tem exatamente o nome da cor (grupos de outros nomes
    // ficam de fora dessas colunas, mas entram no total de produtos)
    private List<PedidoFinalLinhaDTO> montarLinhasFinal(List<Pedido> pedidos){
        // agrupa pelo id do cliente, não pelo código
        Map<Object, List<Pedido>> porCliente = new LinkedHashMap<>();
        for (Pedido pedido : pedidos) {
            Object chave = pedido.getCliente() != null ? pedido.getCliente().getId() : "sem-cliente";
            porCliente.computeIfAbsent(chave, k -> new ArrayList<>()).add(pedido);
        }

        List<PedidoFinalLinhaDTO> linhas = new ArrayList<>();
        for (List<Pedido> doCliente : porCliente.values()) {
            Pedido primeiro = doCliente.get(0);
            int totalProdutos = 0;
            int amarelo = 0, rosa = 0, laranja = 0, verde = 0;
            LocalDate primeiraEntrega = null;
            LocalDate primeiraCriacao = null;

            for (Pedido pedido : doCliente) {
                if (pedido.getDataEntrega() != null && (primeiraEntrega == null || pedido.getDataEntrega().isBefore(primeiraEntrega))) {
                    primeiraEntrega = pedido.getDataEntrega();
                }
                if (pedido.getCreatedAt() != null && (primeiraCriacao == null || pedido.getCreatedAt().toLocalDate().isBefore(primeiraCriacao))) {
                    primeiraCriacao = pedido.getCreatedAt().toLocalDate();
                }
                for (PedidoProduto item : pedido.getProdutos()) {
                    int quantidade = item.getQuantidade() != null ? item.getQuantidade() : 0;
                    totalProdutos += quantidade;
                    for (Grupo grupo : gruposDe(item)) {
                        switch (normalizar(grupo.getNome())) {
                            case "amarelo" -> amarelo += quantidade;
                            case "rosa" -> rosa += quantidade;
                            case "laranja" -> laranja += quantidade;
                            case "verde" -> verde += quantidade;
                            default -> { }
                        }
                    }
                }
            }

            linhas.add(new PedidoFinalLinhaDTO(
                    codigoClienteDe(primeiro), nomeClienteDe(primeiro), cidadeDe(primeiro),
                    data(primeiraEntrega), data(primeiraCriacao),
                    String.valueOf(totalProdutos), String.valueOf(doCliente.size()),
                    String.valueOf(amarelo), String.valueOf(rosa), String.valueOf(laranja), String.valueOf(verde)));
        }
        linhas.sort(Comparator.comparing(PedidoFinalLinhaDTO::getNomeCliente, String.CASE_INSENSITIVE_ORDER));
        return linhas;
    }

    // mesma regra da tela (PedidoProdutoMapper.grupoNomeDe): grupos de produto do cadastro do produto;
    // se não tiver, os da joia; se não tiver, o grupo escolhido manualmente no item avulso
    private static List<Grupo> gruposDe(PedidoProduto item){
        if (item.getProduto() != null && item.getProduto().getGrupos() != null) {
            List<Grupo> grupos = gruposDeProduto(item.getProduto().getGrupos());
            if (!grupos.isEmpty()) return grupos;
        }
        if (item.getJoia() != null && item.getJoia().getGrupos() != null) {
            List<Grupo> grupos = gruposDeProduto(item.getJoia().getGrupos());
            if (!grupos.isEmpty()) return grupos;
        }
        return item.getGrupo() != null ? List.of(item.getGrupo()) : List.of();
    }

    private static List<Grupo> gruposDeProduto(List<Grupo> grupos){
        return grupos.stream().filter(g -> g.getTipo() == TipoGrupo.PRODUTO).toList();
    }

    // compara nome do grupo com a cor ignorando maiúsculas, acentos e espaços nas pontas
    private static String normalizar(String nome){
        if (nome == null) return "";
        return Normalizer.normalize(nome.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private List<Pedido> carregar(List<Long> ids){
        if (ids == null || ids.isEmpty()) {
            throw new ItemInvalidoException("Informe ao menos um pedido");
        }
        List<Long> distintos = ids.stream().distinct().toList();
        List<Pedido> pedidos = new ArrayList<>(repository.findAllById(distintos));
        if (pedidos.size() != distintos.size()) {
            List<Long> encontrados = pedidos.stream().map(Pedido::getId).toList();
            List<Long> faltando = distintos.stream().filter(id -> !encontrados.contains(id)).toList();
            throw new ObjetoNaoEncontradoException("Pedido(s) não encontrado(s). ids: " + faltando);
        }
        return pedidos;
    }

    private void marcarComoImpresso(List<Pedido> pedidos){
        LocalDateTime agora = LocalDateTime.now();
        for (Pedido pedido : pedidos) {
            pedido.setImpresso(true);
            pedido.setImpressoEm(agora);
        }
    }

    private JasperPrint preencher(String relatorio, List<?> linhas){
        Map<String, Object> params = new HashMap<>();
        // a logo vem do classpath (resources/images); se não existir, o relatório sai sem logo
        try (InputStream jasper = getClass().getResourceAsStream(relatorio);
             InputStream logoStream = getClass().getResourceAsStream(LOGO)) {
            params.put("LOGO", logoStream);
            return JasperFillManager.fillReport(jasper, params, new JRBeanCollectionDataSource(linhas));
        } catch (JRException | IOException e) {
            throw new IllegalStateException("Erro ao gerar o relatório de pedidos: " + e.getMessage(), e);
        }
    }

    // os dois relatórios têm tamanho de página diferente (retrato e paisagem), então vão como
    // documentos separados dentro do mesmo exportador, que respeita o tamanho de cada um
    private byte[] exportar(List<JasperPrint> prints){
        try {
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            JRPdfExporter exporter = new JRPdfExporter();
            exporter.setExporterInput(SimpleExporterInput.getInstance(prints));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(saida));
            exporter.exportReport();
            return saida.toByteArray();
        } catch (JRException e) {
            throw new IllegalStateException("Erro ao gerar o relatório de pedidos: " + e.getMessage(), e);
        }
    }

    private static String codigoDe(PedidoProduto item){
        if (item.getProduto() != null) return item.getProduto().getSku() != null ? item.getProduto().getSku() : "-";
        if (item.getJoia() != null) return item.getJoia().getCodigo() != null ? item.getJoia().getCodigo() : "-";
        return "-";
    }

    private static String nomeDe(PedidoProduto item){
        if (item.getProduto() != null) return item.getProduto().getNome();
        if (item.getJoia() != null) return item.getJoia().getNome();
        return item.getDescricao() != null ? item.getDescricao() : "";
    }

    private static String variacaoDe(PedidoProduto item){
        if (item.getVariacao() != null && !item.getVariacao().isBlank()) return item.getVariacao();
        return item.getProdutoVariacao() != null ? item.getProdutoVariacao().getSku() : null;
    }

    private static String codigoClienteDe(Pedido pedido){
        return pedido.getCliente() != null && pedido.getCliente().getCodigo() != null ? pedido.getCliente().getCodigo() : "";
    }

    private static String nomeClienteDe(Pedido pedido){
        return pedido.getCliente() != null && pedido.getCliente().getNome() != null ? pedido.getCliente().getNome() : "";
    }

    private static String cidadeDe(Pedido pedido){
        if (pedido.getCliente() == null) return "";
        var endereco = pedido.getCliente().getEnderecoPrincipal();
        return endereco != null && endereco.getMunicipio() != null ? endereco.getMunicipio() : "";
    }

    private static String moeda(BigDecimal valor){
        return NumberFormat.getCurrencyInstance(PT_BR).format(valor != null ? valor : BigDecimal.ZERO);
    }

    private static String data(LocalDate data){
        return data != null ? data.format(DATA) : "";
    }

    private static String data(LocalDateTime data){
        return data != null ? data.format(DATA) : "";
    }
}
