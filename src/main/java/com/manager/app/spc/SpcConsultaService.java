package com.manager.app.spc;

import com.manager.app.cliente.Cliente;
import com.manager.app.cliente.ClienteService;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.spc.client.SpcClient;
import com.manager.app.spc.client.SpcXmlBuilder;
import com.manager.app.spc.client.SpcXmlStorageService;
import com.manager.app.spc.dto.SpcConsultaRequestDTO;
import com.manager.app.spc.dto.SpcConsultaResponseDTO;
import com.manager.app.spc.dto.SpcConsultaResumoDTO;
import com.manager.app.spc.dto.SpcInsumoResultadoDTO;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpcConsultaService {

    private static final Set<String> INSUMOS_RESTRICAO = Set.of(
            "spc", "protesto", "pendencia-financeira", "acao", "informacao-poder-judiciario");

    private final ClienteService clienteService;
    private final SpcCatalogoService catalogoService;
    private final SpcConsultaRepository repository;
    private final SpcXmlBuilder xmlBuilder;
    private final SpcClient client;
    private final SpcXmlStorageService xmlStorageService;
    private final SpcConsultaResponseParser responseParser;
    private final SpcInsumoGenericoMapper mapper;

    public SpcConsultaResponseDTO consultar(SpcConsultaRequestDTO request){
        Cliente cliente = clienteService.findByIdAndAtivo(request.getClienteId());
        SpcProduto produto = catalogoService.buscarProduto(request.getCodigoProduto());

        if (!produto.aceita(request.getTipoPessoa())) {
            throw new ItemInvalidoException("Produto " + produto.getCodigo() + " não aceita tipo de pessoa " + request.getTipoPessoa());
        }

        List<Integer> insumosOpcionais = request.getInsumosOpcionais() != null ? request.getInsumosOpcionais() : List.of();
        for (Integer codigoInsumo : insumosOpcionais) {
            if (!catalogoService.insumoOpcionalValido(produto.getCodigo(), codigoInsumo)) {
                throw new ItemInvalidoException("Insumo opcional " + codigoInsumo + " não pertence ao produto " + produto.getCodigo());
            }
        }

        String documento = request.getTipoPessoa() == TipoPessoaSpc.F ? cliente.getCpf() : cliente.getDocumento();
        String xmlRequest = xmlBuilder.montarConsulta(produto.getCodigo(), request.getTipoPessoa().name(), documento, insumosOpcionais);
        String xmlResponse = client.consultar(xmlRequest);

        SpcConsultaResponseDTO dto = mapper.mapear(responseParser.parse(xmlResponse));

        SpcConsulta consulta = new SpcConsulta();
        consulta.setCliente(cliente);
        consulta.setTipoPessoa(request.getTipoPessoa());
        consulta.setCodigoProduto(produto.getCodigo());
        consulta.setProtocoloNumero(dto.getProtocoloNumero());
        consulta.setProtocoloDigito(dto.getProtocoloDigito());
        consulta.setInsumosOpcionaisSolicitados(insumosOpcionais.stream().map(String::valueOf).collect(Collectors.joining(",")));
        consulta.setPossuiRestricao(possuiRestricao(dto));
        consulta = repository.save(consulta);

        xmlStorageService.salvarConsulta(consulta.getId(), xmlRequest, xmlResponse);

        dto.setId(consulta.getId());
        dto.setCreatedAt(consulta.getCreatedAt());
        return dto;
    }

    public SpcConsultaResponseDTO buscarPorId(UUID id){
        SpcConsulta consulta = repository.findById(id)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Consulta SPC não encontrada. id: " + id));
        String xmlResponse = xmlStorageService.lerRespostaConsulta(consulta.getId());
        SpcConsultaResponseDTO dto = mapper.mapear(responseParser.parse(xmlResponse));
        dto.setId(consulta.getId());
        dto.setCreatedAt(consulta.getCreatedAt());
        return dto;
    }

    public Page<SpcConsultaResumoDTO> findAll(UUID clienteId, Pageable pageable){
        Specification<SpcConsulta> spec = null;
        if (clienteId != null) {
            spec = (root, query, cb) -> cb.equal(root.get("cliente").get("id"), clienteId);
        }
        return repository.findAll(spec, pageable).map(this::toResumoDTO);
    }

    public byte[] gerarRelatorio(UUID id){
        SpcConsulta consulta = repository.findById(id)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Consulta SPC não encontrada. id: " + id));
        SpcConsultaResponseDTO dto = buscarPorId(id);
        List<Map<String, ?>> linhas = montarLinhasRelatorio(consulta, dto);

        try {
            InputStream jasperStream = getClass().getResourceAsStream("/reports/spc/relSpcConsulta.jasper");
            JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource(linhas);
            JasperPrint print = JasperFillManager.fillReport(jasperStream, new HashMap<>(), dataSource);
            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar relatório de consulta SPC", e);
        }
    }

    private List<Map<String, ?>> montarLinhasRelatorio(SpcConsulta consulta, SpcConsultaResponseDTO dto){
        List<Map<String, ?>> linhas = new ArrayList<>();

        adicionarLinha(linhas, "TITULO", "Protocolo: " + dto.getProtocoloNumero() + "-" + dto.getProtocoloDigito());
        adicionarLinha(linhas, "TEXTO", "Cliente: " + consulta.getCliente().getNome());
        adicionarLinha(linhas, "TEXTO", "Produto: " + catalogoService.buscarProduto(consulta.getCodigoProduto()).getNome()
                + " (" + consulta.getCodigoProduto() + ")");
        adicionarLinha(linhas, "TEXTO", "Data da consulta: " + consulta.getCreatedAt());
        adicionarLinha(linhas, "TEXTO", "Possui restrição: " + (Boolean.TRUE.equals(dto.getRestricao()) ? "SIM" : "NÃO"));
        adicionarLinha(linhas, "SEPARADOR", "");

        if (dto.getConsumidor() != null && !dto.getConsumidor().isEmpty()) {
            adicionarLinha(linhas, "SUBTITULO", "Consumidor");
            for (Map.Entry<String, Object> entrada : dto.getConsumidor().entrySet()) {
                adicionarLinha(linhas, "TEXTO", "   " + entrada.getKey() + ": " + entrada.getValue());
            }
            adicionarLinha(linhas, "SEPARADOR", "");
        }

        if (dto.getAvisos() != null && !dto.getAvisos().isEmpty()) {
            adicionarLinha(linhas, "SUBTITULO", "Avisos");
            for (String aviso : dto.getAvisos()) {
                adicionarLinha(linhas, "TEXTO", "   " + aviso);
            }
            adicionarLinha(linhas, "SEPARADOR", "");
        }

        for (SpcInsumoResultadoDTO insumo : dto.getInsumos()) {
            StringBuilder titulo = new StringBuilder(insumo.getNome().toUpperCase());
            if (insumo.getResumo() != null && insumo.getResumo().getQuantidadeTotal() != null) {
                titulo.append(" (").append(insumo.getResumo().getQuantidadeTotal()).append(" ocorrência(s)");
                if (insumo.getResumo().getValorTotal() != null) {
                    titulo.append(", total R$ ").append(insumo.getResumo().getValorTotal());
                }
                titulo.append(")");
            }
            adicionarLinha(linhas, "SUBTITULO", titulo.toString());

            List<Map<String, Object>> detalhes = insumo.getDetalhes();
            int indice = 1;
            for (Map<String, Object> detalhe : detalhes) {
                if (detalhes.size() > 1) {
                    adicionarLinha(linhas, "TEXTO", "   Ocorrência " + (indice++) + ":");
                }
                for (Map.Entry<String, Object> entrada : detalhe.entrySet()) {
                    adicionarLinha(linhas, "TEXTO", "     " + entrada.getKey() + ": " + entrada.getValue());
                }
            }
            adicionarLinha(linhas, "SEPARADOR", "");
        }

        return linhas;
    }

    private void adicionarLinha(List<Map<String, ?>> linhas, String tipo, String texto){
        Map<String, Object> linha = new HashMap<>();
        linha.put("tipo", tipo);
        linha.put("texto", texto);
        linhas.add(linha);
    }

    private boolean possuiRestricao(SpcConsultaResponseDTO dto){
        for (SpcInsumoResultadoDTO insumo : dto.getInsumos()) {
            if (!INSUMOS_RESTRICAO.contains(insumo.getNome())) continue;
            Integer quantidade = insumo.getResumo() != null ? insumo.getResumo().getQuantidadeTotal() : null;
            if (quantidade == null || quantidade > 0) return true;
        }
        return false;
    }

    private SpcConsultaResumoDTO toResumoDTO(SpcConsulta consulta){
        SpcConsultaResumoDTO dto = new SpcConsultaResumoDTO();
        dto.setId(consulta.getId());
        dto.setClienteId(consulta.getCliente().getId());
        dto.setClienteNome(consulta.getCliente().getNome());
        dto.setCodigoProduto(consulta.getCodigoProduto());
        dto.setNomeProduto(catalogoService.buscarProduto(consulta.getCodigoProduto()).getNome());
        dto.setProtocoloNumero(consulta.getProtocoloNumero());
        dto.setPossuiRestricao(consulta.isPossuiRestricao());
        dto.setCreatedAt(consulta.getCreatedAt());
        return dto;
    }
}
