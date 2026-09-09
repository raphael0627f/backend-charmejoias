package com.manager.app.acertojoia;

import com.manager.app.acertojoia.dto.AcertoJoiaConsignacaoNovaResquestDTO;
import com.manager.app.acertojoia.dto.AcertoJoiaRequestDTO;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AcertoJoiaService {

    private final AcertoJoiaRepository repository;
    private final ConsignacaoRepository consignacaoRepository;
    private final ConsignacaoService consignacaoService;
    private final AcertoJoiaValidation validation;

    public Page<AcertoJoia> findAll(String busca, LocalDate dataInicio, LocalDate dataFim, Pageable pageable){
        Specification<AcertoJoia> spec = comBusca(busca);

        if (dataInicio != null) {
            Specification<AcertoJoia> dataInicioSpec = (root, query, cb) ->
                    SpecificationUtils.criadoAPartirDe(cb, root.get("createdAt"), dataInicio);
            spec = spec == null ? dataInicioSpec : spec.and(dataInicioSpec);
        }

        if (dataFim != null) {
            Specification<AcertoJoia> dataFimSpec = (root, query, cb) ->
                    SpecificationUtils.criadoAte(cb, root.get("createdAt"), dataFim);
            spec = spec == null ? dataFimSpec : spec.and(dataFimSpec);
        }

        return repository.findAll(spec, pageable);
    }

    private Specification<AcertoJoia> comBusca(String busca){
        if (busca == null || busca.isBlank()) return null;
        Long comoNumero = SpecificationUtils.comoLong(busca);
        if (comoNumero == null) return (root, query, cb) -> cb.disjunction();
        return (root, query, cb) -> cb.equal(root.get("id"), comoNumero);
    }

    public AcertoJoia findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Acerto de joia não encontrado. id: " + id));
    }

    @Transactional
    public AcertoJoia create(AcertoJoiaRequestDTO request){
        AcertoJoia acertoJoia = new AcertoJoia();

        acertoJoia.setValorConsignacoes(request.getValorConsignacoes());
        acertoJoia.setValorMaleta(request.getValorMaleta());
        acertoJoia.setValorAcessorios(request.getValorAcessorios());

        List<Consignacao> consignacoes = consignacaoRepository.findAllById(request.getConsignacoesIds());
        List<Consignacao> maletas = consignacaoRepository.findAllById(request.getMaletasIds());
        List<Consignacao> acessorios = consignacaoRepository.findAllById(request.getAcessoriosIds());


        for (Consignacao consignacao : consignacoes){
            AcertoJoiaConsignacao consignacaoAcerto = new AcertoJoiaConsignacao();

            consignacaoAcerto.setConsignacao(consignacao);
            consignacaoAcerto.setDescricao(consignacao.getDescricao());
            consignacaoAcerto.setQuantidadeProdutos(consignacao.getQuantidadeTotal());
            consignacaoAcerto.setValorTotal(consignacao.getValorTotal());
            consignacaoAcerto.setTipo(TipoConsignacaoAcertoJoia.KIT);
            acertoJoia.adicionarConsignacao(consignacaoAcerto);
        }

        for (Consignacao maleta : maletas){
            AcertoJoiaConsignacao consignacaoAcerto = new AcertoJoiaConsignacao();

            consignacaoAcerto.setConsignacao(maleta);
            consignacaoAcerto.setDescricao(maleta.getDescricao());
            consignacaoAcerto.setQuantidadeProdutos(maleta.getQuantidadeTotal());
            consignacaoAcerto.setValorTotal(maleta.getValorTotal());
            consignacaoAcerto.setTipo(TipoConsignacaoAcertoJoia.MALETA);
            acertoJoia.adicionarConsignacao(consignacaoAcerto);
        }

        for (Consignacao acessorio : acessorios){
            AcertoJoiaConsignacao consignacaoAcerto = new AcertoJoiaConsignacao();

            consignacaoAcerto.setConsignacao(acessorio);
            consignacaoAcerto.setDescricao(acessorio.getDescricao());
            consignacaoAcerto.setQuantidadeProdutos(acessorio.getQuantidadeTotal());
            consignacaoAcerto.setValorTotal(acessorio.getValorTotal());
            consignacaoAcerto.setTipo(TipoConsignacaoAcertoJoia.ACESSORIO);
            acertoJoia.adicionarConsignacao(consignacaoAcerto);
        }

        for(AcertoJoiaConsignacaoNovaResquestDTO consignacaoNova : request.getConsignacaoesNovas()){
            AcertoJoiaConsignacao consignacaoAcerto = new AcertoJoiaConsignacao();

            consignacaoAcerto.setConsignacao(null);
            consignacaoAcerto.setDescricao(consignacaoNova.getDescricao());
            consignacaoAcerto.setQuantidadeProdutos(consignacaoNova.getQuantidadeProdutos());
            consignacaoAcerto.setValorTotal(consignacaoNova.getValorTotal());
            consignacaoAcerto.setTipo(TipoConsignacaoAcertoJoia.KIT_NOVO);
            acertoJoia.adicionarConsignacao(consignacaoAcerto);
        }

        return repository.save(acertoJoia);
    }

    @Transactional
    public AcertoJoia update(AcertoJoiaRequestDTO request){
        AcertoJoia acertoUpdate = this.findById(request.getId());

        acertoUpdate.setValorConsignacoes(request.getValorConsignacoes());
        acertoUpdate.setValorMaleta(request.getValorMaleta());
        acertoUpdate.setValorAcessorios(request.getValorAcessorios());

        List<AcertoJoiaConsignacao> consignacoesAtuais = new ArrayList<>(acertoUpdate.getConsignacoes());
        List<AcertoJoiaConsignacao> consignacoesRequest = new ArrayList<>();

        for (Long consignacaoId : request.getConsignacoesIds()){
            Consignacao consignacao = consignacaoService.findById(consignacaoId);
            AcertoJoiaConsignacao acertoConsignacao = new AcertoJoiaConsignacao();

            acertoConsignacao.setDescricao(consignacao.getDescricao());
            acertoConsignacao.setQuantidadeProdutos(consignacao.getQuantidadeTotal());
            acertoConsignacao.setTipo(TipoConsignacaoAcertoJoia.KIT);
            acertoConsignacao.setValorTotal(consignacao.getValorTotal());
            acertoConsignacao.setConsignacao(consignacao);
            acertoConsignacao.setAcertoJoia(acertoUpdate);

            consignacoesRequest.add(acertoConsignacao);
        }

        for (Long consignacaoId : request.getMaletasIds()){
            Consignacao consignacao = consignacaoService.findById(consignacaoId);
            AcertoJoiaConsignacao acertoConsignacao = new AcertoJoiaConsignacao();

            acertoConsignacao.setDescricao(consignacao.getDescricao());
            acertoConsignacao.setQuantidadeProdutos(consignacao.getQuantidadeTotal());
            acertoConsignacao.setTipo(TipoConsignacaoAcertoJoia.MALETA);
            acertoConsignacao.setValorTotal(consignacao.getValorTotal());
            acertoConsignacao.setConsignacao(consignacao);
            acertoConsignacao.setAcertoJoia(acertoUpdate);

            consignacoesRequest.add(acertoConsignacao);
        }

        for (Long consignacaoId : request.getAcessoriosIds()){
            Consignacao consignacao = consignacaoService.findById(consignacaoId);
            AcertoJoiaConsignacao acertoConsignacao = new AcertoJoiaConsignacao();

            acertoConsignacao.setDescricao(consignacao.getDescricao());
            acertoConsignacao.setQuantidadeProdutos(consignacao.getQuantidadeTotal());
            acertoConsignacao.setTipo(TipoConsignacaoAcertoJoia.ACESSORIO);
            acertoConsignacao.setValorTotal(consignacao.getValorTotal());
            acertoConsignacao.setConsignacao(consignacao);
            acertoConsignacao.setAcertoJoia(acertoUpdate);

            consignacoesRequest.add(acertoConsignacao);
        }

        for (AcertoJoiaConsignacao consignacaoRequest : consignacoesRequest){
            AcertoJoiaConsignacao consignacaoExistente = null;

            for (AcertoJoiaConsignacao consignacao : acertoUpdate.getConsignacoes()){
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

        for (AcertoJoiaConsignacaoNovaResquestDTO consignacaoNovaDTO : request.getConsignacaoesNovas()){
            AcertoJoiaConsignacao consignacaoNova = new AcertoJoiaConsignacao();

            consignacaoNova.setDescricao(consignacaoNovaDTO.getDescricao());
            consignacaoNova.setQuantidadeProdutos(consignacaoNovaDTO.getQuantidadeProdutos());
            consignacaoNova.setTipo(TipoConsignacaoAcertoJoia.KIT_NOVO);
            consignacaoNova.setValorTotal(consignacaoNovaDTO.getValorTotal());
            consignacaoNova.setConsignacao(null);

            acertoUpdate.adicionarConsignacao(consignacaoNova);
        }

        for (AcertoJoiaConsignacao removerConsignacao : consignacoesAtuais) {
            acertoUpdate.removerConsignacao(removerConsignacao);
        }

        return acertoUpdate;
    }

    private BigDecimal nullSafe(BigDecimal valor){
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private BigDecimal somaPorTipo(AcertoJoia acertoJoia, TipoConsignacaoAcertoJoia tipo){
        BigDecimal total = BigDecimal.ZERO;

        for (AcertoJoiaConsignacao consignacao : acertoJoia.getConsignacoes()){
            if (consignacao.getTipo() == tipo){
                total = total.add(nullSafe(consignacao.getValorTotal()));
            }
        }

        return total;
    }

    private List<JasperPrint> gerarPaginasConsignacoes(AcertoJoia acertoJoia){
        List<JasperPrint> paginas = new ArrayList<>();

        try {
            for (AcertoJoiaConsignacao acertoConsignacao : acertoJoia.getConsignacoes()){
                if (acertoConsignacao.getConsignacao() == null) continue;

                JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource(consignacaoService.dataConsignacao(acertoConsignacao.getConsignacao()));
                InputStream jasperStream = getClass().getResourceAsStream("/reports/acertojoia/relAcerto.jasper");
                paginas.add(JasperFillManager.fillReport(jasperStream, new HashMap<>(), dataSource));
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar relatório de acerto de joia", e);
        }

        return paginas;
    }

    private String descricaoOuFallback(AcertoJoiaConsignacao consignacao){
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

    private List<Map<String, ?>> dataSubRelatorioKitOuNovo(AcertoJoia acertoJoia, TipoConsignacaoAcertoJoia tipo){
        List<Map<String, ?>> linhas = new ArrayList<>();

        for (AcertoJoiaConsignacao consignacao : acertoJoia.getConsignacoes()){
            if (consignacao.getTipo() != tipo) continue;

            Map<String, Object> linha = new HashMap<>();
            linha.put("NumeroKIT", descricaoOuFallback(consignacao));
            linha.put("QTDEPecas", consignacao.getQuantidadeProdutos());
            linha.put("ValorTotal", nullSafe(consignacao.getValorTotal()).doubleValue());
            linhas.add(linha);
        }

        return linhas;
    }

    private List<Map<String, ?>> dataSubRelatorioMaleta(AcertoJoia acertoJoia){
        List<Map<String, ?>> linhas = new ArrayList<>();

        for (AcertoJoiaConsignacao consignacao : acertoJoia.getConsignacoes()){
            if (consignacao.getTipo() != TipoConsignacaoAcertoJoia.MALETA) continue;

            Map<String, Object> linha = new HashMap<>();
            linha.put("Pecas", descricaoOuFallback(consignacao));
            linha.put("QTDE", consignacao.getQuantidadeProdutos());
            linha.put("ValorTotal", nullSafe(consignacao.getValorTotal()).doubleValue());
            linhas.add(linha);
        }

        return linhas;
    }

    private Map<String, ?> dataAcertoFinal(AcertoJoia acertoJoia){
        BigDecimal valorTotalKits = nullSafe(acertoJoia.getValorConsignacoes());
        BigDecimal valorTotalMaleta = nullSafe(acertoJoia.getValorMaleta());
        BigDecimal valorTotalAcessorios = nullSafe(acertoJoia.getValorAcessorios());

        BigDecimal totalMercadorias = valorTotalKits.add(valorTotalMaleta);
        BigDecimal devolvidoMercadorias = somaPorTipo(acertoJoia, TipoConsignacaoAcertoJoia.KIT)
                .add(somaPorTipo(acertoJoia, TipoConsignacaoAcertoJoia.KIT_NOVO))
                .add(somaPorTipo(acertoJoia, TipoConsignacaoAcertoJoia.MALETA));
        BigDecimal diferencaMercadorias = totalMercadorias.subtract(devolvidoMercadorias);
        BigDecimal totalPagarMercadorias = acertoJoia.valorPagarConsignacoesMaleta();

        BigDecimal acessoriosDevolvido = somaPorTipo(acertoJoia, TipoConsignacaoAcertoJoia.ACESSORIO);
        BigDecimal diferencaAcessorios = valorTotalAcessorios.subtract(acessoriosDevolvido);
        BigDecimal totalAcessorios = acertoJoia.valorPagarAcessorios();

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

    private JasperPrint gerarPaginaFinal(AcertoJoia acertoJoia){
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("SUBREPORT_KIT", getClass().getResourceAsStream("/reports/acertojoia/sub/subRelAcertoFinalKIT.jasper"));
            params.put("SUBREPORT_MALETA", getClass().getResourceAsStream("/reports/acertojoia/sub/subRelAcertoFinalMaleta.jasper"));
            params.put("SUBREPORT_NOVO", getClass().getResourceAsStream("/reports/acertojoia/sub/subRelAcertoFinalNovo.jasper"));
            params.put("DS_KIT", new JRMapCollectionDataSource(dataSubRelatorioKitOuNovo(acertoJoia, TipoConsignacaoAcertoJoia.KIT)));
            params.put("DS_MALETA", new JRMapCollectionDataSource(dataSubRelatorioMaleta(acertoJoia)));
            params.put("DS_NOVO", new JRMapCollectionDataSource(dataSubRelatorioKitOuNovo(acertoJoia, TipoConsignacaoAcertoJoia.KIT_NOVO)));

            JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource(List.of(dataAcertoFinal(acertoJoia)));
            InputStream jasperStream = getClass().getResourceAsStream("/reports/acertojoia/relAcertoFinal.jasper");
            return JasperFillManager.fillReport(jasperStream, params, dataSource);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar relatório de acerto de joia", e);
        }
    }

    public byte[] gerarRelatorio(Long id){
        try {
            AcertoJoia acertoJoia = this.findById(id);

            List<JasperPrint> paginas = new ArrayList<>();
            paginas.add(gerarPaginaFinal(acertoJoia));
            paginas.addAll(gerarPaginasConsignacoes(acertoJoia));

            return exportarPdf(paginas);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar relatório de acerto de joia", e);
        }
    }

    public byte[] gerarRelatorioFinal(Long id){
        try {
            AcertoJoia acertoJoia = this.findById(id);
            return exportarPdf(List.of(gerarPaginaFinal(acertoJoia)));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar relatório de acerto de joia", e);
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
