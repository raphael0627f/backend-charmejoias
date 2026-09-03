package com.manager.app.acerto;

import com.manager.app.acerto.dto.AcertoConsignacaoNovaResquestDTO;
import com.manager.app.acerto.dto.AcertoRequestDTO;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.specification.SpecificationUtils;
import com.manager.app.consignacao.ConsignacaoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import com.manager.app.consignacao.Consignacao;
import com.manager.app.consignacao.ConsignacaoService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AcertoService {

    private final AcertoRepository repository;
    private final ConsignacaoRepository consignacaoRepository;
    private final ConsignacaoService consignacaoService;
    private final AcertoValidation validation;

    public Page<Acerto> findAll(String busca, Pageable pageable){
        return repository.findAll(comBusca(busca), pageable);
    }

    private Specification<Acerto> comBusca(String busca){
        if (busca == null || busca.isBlank()) return null;
        return (root, query, cb) -> cb.or(
                SpecificationUtils.likeIgnoreCase(cb, root.get("id").as(String.class), busca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("createdAt").as(String.class), busca)
        );
    }

    public Acerto findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Acerto não encontrada. id: " + id));
    }

    @Transactional
    public Acerto create(AcertoRequestDTO request){
        Acerto acerto = new Acerto();

        acerto.setValorConsignacoes(request.getValorConsignacoes());
        acerto.setValorMaleta(request.getValorMaleta());
        acerto.setValorAcessorios(request.getValorAcessorios());

        List<Consignacao> consignacoes = consignacaoRepository.findAllById(request.getConsignacoesIds());
        List<Consignacao> maletas = consignacaoRepository.findAllById(request.getMaletasIds());
        List<Consignacao> acessorios = consignacaoRepository.findAllById(request.getAcessoriosIds());


        for (Consignacao consignacao : consignacoes){
            AcertoConsignacao consignacaoAcerto = new AcertoConsignacao();

            consignacaoAcerto.setConsignacao(consignacao);
            consignacaoAcerto.setDescricao(consignacao.getDescricao());
            consignacaoAcerto.setQuantidadeProdutos(consignacao.getQuantidadeTotal());
            consignacaoAcerto.setValorTotal(consignacao.getValorTotal());
            consignacaoAcerto.setTipo(TipoConsignacaoAcerto.KIT);
            acerto.adicionarConsignacao(consignacaoAcerto);
        }

        for (Consignacao maleta : maletas){
            AcertoConsignacao consignacaoAcerto = new AcertoConsignacao();

            consignacaoAcerto.setConsignacao(maleta);
            consignacaoAcerto.setDescricao(maleta.getDescricao());
            consignacaoAcerto.setQuantidadeProdutos(maleta.getQuantidadeTotal());
            consignacaoAcerto.setValorTotal(maleta.getValorTotal());
            consignacaoAcerto.setTipo(TipoConsignacaoAcerto.MALETA);
            acerto.adicionarConsignacao(consignacaoAcerto);
        }

        for (Consignacao acessorio : acessorios){
            AcertoConsignacao consignacaoAcerto = new AcertoConsignacao();

            consignacaoAcerto.setConsignacao(acessorio);
            consignacaoAcerto.setDescricao(acessorio.getDescricao());
            consignacaoAcerto.setQuantidadeProdutos(acessorio.getQuantidadeTotal());
            consignacaoAcerto.setValorTotal(acessorio.getValorTotal());
            consignacaoAcerto.setTipo(TipoConsignacaoAcerto.ACESSORIO);
            acerto.adicionarConsignacao(consignacaoAcerto);
        }

        for(AcertoConsignacaoNovaResquestDTO consignacaoNova : request.getConsignacaoesNovas()){
            AcertoConsignacao consignacaoAcerto = new AcertoConsignacao();

            consignacaoAcerto.setConsignacao(null);
            consignacaoAcerto.setDescricao(consignacaoNova.getDescricao());
            consignacaoAcerto.setQuantidadeProdutos(consignacaoNova.getQuantidadeProdutos());
            consignacaoAcerto.setValorTotal(consignacaoNova.getValorTotal());
            consignacaoAcerto.setTipo(TipoConsignacaoAcerto.KIT_NOVO);
            acerto.adicionarConsignacao(consignacaoAcerto);
        }

        return repository.save(acerto);
    }

    @Transactional
    public Acerto update(AcertoRequestDTO request){
        Acerto acertoUpdate = this.findById(request.getId());

        acertoUpdate.setValorConsignacoes(request.getValorConsignacoes());
        acertoUpdate.setValorMaleta(request.getValorMaleta());
        acertoUpdate.setValorAcessorios(request.getValorAcessorios());

        List<AcertoConsignacao> consignacoesAtuais = new ArrayList<>(acertoUpdate.getConsignacoes());
        List<AcertoConsignacao> consignacoesRequest = new ArrayList<>();

        for (Long consignacaoId : request.getConsignacoesIds()){
            Consignacao consignacao = consignacaoService.findById(consignacaoId);
            AcertoConsignacao acertoConsignacao = new AcertoConsignacao();

            acertoConsignacao.setDescricao(consignacao.getDescricao());
            acertoConsignacao.setQuantidadeProdutos(consignacao.getQuantidadeTotal());
            acertoConsignacao.setTipo(TipoConsignacaoAcerto.KIT);
            acertoConsignacao.setValorTotal(consignacao.getValorTotal());
            acertoConsignacao.setConsignacao(consignacao);
            acertoConsignacao.setAcerto(acertoUpdate);

            consignacoesRequest.add(acertoConsignacao);
        }

        for (Long consignacaoId : request.getMaletasIds()){
            Consignacao consignacao = consignacaoService.findById(consignacaoId);
            AcertoConsignacao acertoConsignacao = new AcertoConsignacao();

            acertoConsignacao.setDescricao(consignacao.getDescricao());
            acertoConsignacao.setQuantidadeProdutos(consignacao.getQuantidadeTotal());
            acertoConsignacao.setTipo(TipoConsignacaoAcerto.MALETA);
            acertoConsignacao.setValorTotal(consignacao.getValorTotal());
            acertoConsignacao.setConsignacao(consignacao);
            acertoConsignacao.setAcerto(acertoUpdate);

            consignacoesRequest.add(acertoConsignacao);
        }

        for (Long consignacaoId : request.getAcessoriosIds()){
            Consignacao consignacao = consignacaoService.findById(consignacaoId);
            AcertoConsignacao acertoConsignacao = new AcertoConsignacao();

            acertoConsignacao.setDescricao(consignacao.getDescricao());
            acertoConsignacao.setQuantidadeProdutos(consignacao.getQuantidadeTotal());
            acertoConsignacao.setTipo(TipoConsignacaoAcerto.ACESSORIO);
            acertoConsignacao.setValorTotal(consignacao.getValorTotal());
            acertoConsignacao.setConsignacao(consignacao);
            acertoConsignacao.setAcerto(acertoUpdate);

            consignacoesRequest.add(acertoConsignacao);
        }

        for (AcertoConsignacao consignacaoRequest : consignacoesRequest){
            AcertoConsignacao consignacaoExistente = null;

            for (AcertoConsignacao consignacao : acertoUpdate.getConsignacoes()){
                if (consignacao.getConsignacao() != null && consignacao.getConsignacao().getId().equals(consignacaoRequest.getConsignacao().getId())) {
                    consignacaoExistente = consignacao;
                    break;
                }
            }

            if(consignacaoExistente != null){

                if (!consignacaoExistente.getDescricao().equals(consignacaoRequest.getDescricao())) {
                    consignacaoExistente.setDescricao(consignacaoRequest.getDescricao());
                }

                if (!consignacaoExistente.getQuantidadeProdutos().equals(consignacaoRequest.getQuantidadeProdutos())) {
                    consignacaoExistente.setQuantidadeProdutos(consignacaoRequest.getQuantidadeProdutos());
                }

                if (!consignacaoExistente.getTipo().equals(consignacaoRequest.getTipo())) {
                    consignacaoExistente.setTipo(consignacaoRequest.getTipo());
                }

                if (!consignacaoExistente.getValorTotal().equals(consignacaoRequest.getValorTotal())) {
                    consignacaoExistente.setValorTotal(consignacaoRequest.getValorTotal());
                }

                consignacoesAtuais.remove(consignacaoExistente);
            } else {
                acertoUpdate.adicionarConsignacao(consignacaoRequest);
            }
        }

        for (AcertoConsignacaoNovaResquestDTO consignacaoNovaDTO : request.getConsignacaoesNovas()){
            AcertoConsignacao consignacaoNova = new AcertoConsignacao();

            consignacaoNova.setDescricao(consignacaoNovaDTO.getDescricao());
            consignacaoNova.setQuantidadeProdutos(consignacaoNovaDTO.getQuantidadeProdutos());
            consignacaoNova.setTipo(TipoConsignacaoAcerto.KIT_NOVO);
            consignacaoNova.setValorTotal(consignacaoNovaDTO.getValorTotal());
            consignacaoNova.setConsignacao(null);

            acertoUpdate.adicionarConsignacao(consignacaoNova);
        }

        for (AcertoConsignacao removerConsignacao : consignacoesAtuais) {
            acertoUpdate.removerConsignacao(removerConsignacao);
        }

        return acertoUpdate;
    }

    private BigDecimal nullSafe(BigDecimal valor){
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private BigDecimal somaPorTipo(Acerto acerto, TipoConsignacaoAcerto tipo){
        BigDecimal total = BigDecimal.ZERO;

        for (AcertoConsignacao consignacao : acerto.getConsignacoes()){
            if (consignacao.getTipo() == tipo){
                total = total.add(nullSafe(consignacao.getValorTotal()));
            }
        }

        return total;
    }

    private List<JasperPrint> gerarPaginasConsignacoes(Acerto acerto){
        List<JasperPrint> paginas = new ArrayList<>();

        try {
            for (AcertoConsignacao acertoConsignacao : acerto.getConsignacoes()){
                if (acertoConsignacao.getConsignacao() == null) continue;

                JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource(consignacaoService.dataConsignacao(acertoConsignacao.getConsignacao()));
                InputStream jasperStream = getClass().getResourceAsStream("/reports/acerto/relAcerto.jasper");
                paginas.add(JasperFillManager.fillReport(jasperStream, new HashMap<>(), dataSource));
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar relatório de acerto", e);
        }

        return paginas;
    }

    private String descricaoOuFallback(AcertoConsignacao consignacao){
        if (consignacao.getConsignacao() != null
                && consignacao.getConsignacao().getDescricao() != null
                && !consignacao.getConsignacao().getDescricao().isBlank()){
            return consignacao.getConsignacao().getDescricao();
        }
        if (consignacao.getDescricao() != null && !consignacao.getDescricao().isBlank()){
            return consignacao.getDescricao();
        }
        if (consignacao.getConsignacao() != null){
            return String.valueOf(consignacao.getConsignacao().getId());
        }
        return String.valueOf(consignacao.getId());
    }

    private List<Map<String, ?>> dataSubRelatorioKitOuNovo(Acerto acerto, TipoConsignacaoAcerto tipo){
        List<Map<String, ?>> linhas = new ArrayList<>();

        for (AcertoConsignacao consignacao : acerto.getConsignacoes()){
            if (consignacao.getTipo() != tipo) continue;

            Map<String, Object> linha = new HashMap<>();
            linha.put("NumeroKIT", descricaoOuFallback(consignacao));
            linha.put("QTDEPecas", consignacao.getQuantidadeProdutos());
            linha.put("ValorTotal", nullSafe(consignacao.getValorTotal()).doubleValue());
            linhas.add(linha);
        }

        return linhas;
    }

    private List<Map<String, ?>> dataSubRelatorioMaleta(Acerto acerto){
        List<Map<String, ?>> linhas = new ArrayList<>();

        for (AcertoConsignacao consignacao : acerto.getConsignacoes()){
            if (consignacao.getTipo() != TipoConsignacaoAcerto.MALETA) continue;

            Map<String, Object> linha = new HashMap<>();
            linha.put("Pecas", descricaoOuFallback(consignacao));
            linha.put("QTDE", consignacao.getQuantidadeProdutos());
            linha.put("ValorTotal", nullSafe(consignacao.getValorTotal()).doubleValue());
            linhas.add(linha);
        }

        return linhas;
    }

    private Map<String, ?> dataAcertoFinal(Acerto acerto){
        BigDecimal valorTotalKits = nullSafe(acerto.getValorConsignacoes());
        BigDecimal valorTotalMaleta = nullSafe(acerto.getValorMaleta());
        BigDecimal valorTotalAcessorios = nullSafe(acerto.getValorAcessorios());

        BigDecimal totalMercadorias = valorTotalKits.add(valorTotalMaleta);
        BigDecimal devolvidoMercadorias = somaPorTipo(acerto, TipoConsignacaoAcerto.KIT)
                .add(somaPorTipo(acerto, TipoConsignacaoAcerto.KIT_NOVO))
                .add(somaPorTipo(acerto, TipoConsignacaoAcerto.MALETA));
        BigDecimal diferencaMercadorias = totalMercadorias.subtract(devolvidoMercadorias);
        BigDecimal totalPagarMercadorias = acerto.valorPagarConsignacoesMaleta();

        BigDecimal acessoriosDevolvido = somaPorTipo(acerto, TipoConsignacaoAcerto.ACESSORIO);
        BigDecimal diferencaAcessorios = valorTotalAcessorios.subtract(acessoriosDevolvido);
        BigDecimal totalAcessorios = acerto.valorPagarAcessorios();

        BigDecimal valorTotalAcerto = totalPagarMercadorias.add(totalAcessorios);

        Map<String, Object> linha = new HashMap<>();
        linha.put("ValorTotalKITS", valorTotalKits.doubleValue());
        linha.put("ValorTotalMaleta", valorTotalMaleta.doubleValue());
        linha.put("ValorTotalAcessorios", valorTotalAcessorios.doubleValue());
        linha.put("TotalMercadorias", totalMercadorias.doubleValue());
        linha.put("DiferencaMercadorias", diferencaMercadorias.doubleValue());
        linha.put("TotalPagarMercadorias", totalPagarMercadorias.doubleValue());
        linha.put("ValorTotal", acessoriosDevolvido.doubleValue());
        linha.put("DiferencaAcessorios", diferencaAcessorios.doubleValue());
        linha.put("TotalAcessorios", totalAcessorios.doubleValue());
        linha.put("ValorTotalAcerto", valorTotalAcerto.doubleValue());

        return linha;
    }

    private JasperPrint gerarPaginaFinal(Acerto acerto){
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("SUBREPORT_KIT", getClass().getResourceAsStream("/reports/acerto/sub/subRelAcertoFinalKIT.jasper"));
            params.put("SUBREPORT_MALETA", getClass().getResourceAsStream("/reports/acerto/sub/subRelAcertoFinalMaleta.jasper"));
            params.put("SUBREPORT_NOVO", getClass().getResourceAsStream("/reports/acerto/sub/subRelAcertoFinalNovo.jasper"));
            params.put("DS_KIT", new JRMapCollectionDataSource(dataSubRelatorioKitOuNovo(acerto, TipoConsignacaoAcerto.KIT)));
            params.put("DS_MALETA", new JRMapCollectionDataSource(dataSubRelatorioMaleta(acerto)));
            params.put("DS_NOVO", new JRMapCollectionDataSource(dataSubRelatorioKitOuNovo(acerto, TipoConsignacaoAcerto.KIT_NOVO)));

            JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource(List.of(dataAcertoFinal(acerto)));
            InputStream jasperStream = getClass().getResourceAsStream("/reports/acerto/relAcertoFinal.jasper");
            return JasperFillManager.fillReport(jasperStream, params, dataSource);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar relatório de acerto", e);
        }
    }

    public byte[] gerarRelatorio(Long id){
        try {
            Acerto acerto = this.findById(id);

            List<JasperPrint> paginas = new ArrayList<>();
            paginas.add(gerarPaginaFinal(acerto));
            paginas.addAll(gerarPaginasConsignacoes(acerto));

            return exportarPdf(paginas);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar relatório de acerto", e);
        }
    }

    public byte[] gerarRelatorioFinal(Long id){
        try {
            Acerto acerto = this.findById(id);
            return exportarPdf(List.of(gerarPaginaFinal(acerto)));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar relatório de acerto", e);
        }
    }

    private byte[] exportarPdf(List<JasperPrint> paginas) throws Exception {
        JRPdfExporter exporter = new JRPdfExporter();
        exporter.setExporterInput(SimpleExporterInput.getInstance(paginas));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));
        exporter.exportReport();

        return outputStream.toByteArray();
    }
}


