package com.manager.app.consignacao;

import com.manager.app.consignacao.dto.ClienteResumoDTO;
import com.manager.app.consignacao.dto.ConsignacaoProdutoRequestDTO;
import com.manager.app.consignacao.dto.ConsignacaoRequestDTO;
import com.manager.app.consignacao.dto.ConsignacaoResumoDTO;
import com.manager.app.consignacao.dto.QuantidadePorCategoriaDTO;
import com.manager.app.acertojoia.AcertoJoiaConsignacao;
import com.manager.app.cliente.Cliente;
import com.manager.app.endereco.Endereco;
import com.manager.app.joia.Joia;
import com.manager.app.mostruario.MostruarioService;
import com.manager.app.produto.Produto;
import com.manager.app.produto.ProdutoVariacao;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.ModoBusca;
import com.manager.app.shared.TipoItem;
import com.manager.app.shared.specification.SpecificationUtils;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Subquery;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import com.manager.app.cliente.ClienteService;
import com.manager.app.joia.JoiaService;
import com.manager.app.produto.ProdutoService;
import com.manager.app.usuario.UsuarioResumoMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsignacaoService {

    private static final List<String> ORDEM_CATEGORIAS_PADRAO = List.of(
            "COSMÉTICOS", "ACESSÓRIOS", "LINGERIES", "FANTASIAS", "CALCINHAS", "CUECAS"
    );

    private static final List<String> ORDEM_CATEGORIAS_JOIA = List.of(
            "ANÉIS", "BRINCOS", "CORDÕES", "PINGENTES", "PULSEIRAS", "TORNOZELEIRAS", "ACESSÓRIOS"
    );

    private final ConsignacaoRepository repository;
    private final ConsignacaoProdutoRepository consignacaoProdutoRepository;
    private final com.manager.app.acertoconsignacao.AcertoConsignacaoRepository acertoConsignacaoRepository;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final JoiaService joiaService;
    private final MostruarioService mostruarioService;
    private final ConsignacaoValidation validation;
    private final UsuarioResumoMapper usuarioResumoMapper;
    private final UsuarioAuditorAware usuarioAuditorAware;

    // logo dos relatórios de consignação, buscada numa pasta fora do projeto (mesmo nível da
    // pasta de XMLs de NFe) - se o arquivo não existir lá, o relatório sai sem logo em vez de
    // quebrar a geração
    @Value("${app.storage.relatorio-path}")
    private String relatorioPath;

    public Page<Consignacao> findAll(String busca, ModoBusca modoBusca, UUID clienteId, Boolean temCliente, Boolean temAcerto, Boolean confirmada,
                                      LocalDate dataEntregaInicio, LocalDate dataEntregaFim,
                                      LocalDate dataPreAcertoInicio, LocalDate dataPreAcertoFim,
                                      LocalDate dataAcertoInicio, LocalDate dataAcertoFim,
                                      Pageable pageable){
        Specification<Consignacao> spec = comBusca(busca, modoBusca);

        if (clienteId != null) {
            Specification<Consignacao> clienteIdSpec = (root, query, cb) -> cb.equal(root.get("cliente").get("id"), clienteId);
            spec = SpecificationUtils.and(spec, clienteIdSpec);
        }

        if (temCliente != null) {
            Specification<Consignacao> temClienteSpec = (root, query, cb) -> temCliente
                    ? cb.isNotNull(root.get("cliente"))
                    : cb.isNull(root.get("cliente"));
            spec = SpecificationUtils.and(spec, temClienteSpec);
        }

        if (temAcerto != null) {
            Specification<Consignacao> temAcertoSpec = (root, query, cb) -> {
                Subquery<Long> subquery = query.subquery(Long.class);
                var acertoConsignacaoRoot = subquery.from(AcertoJoiaConsignacao.class);
                subquery.select(acertoConsignacaoRoot.get("id"))
                        .where(cb.equal(acertoConsignacaoRoot.get("consignacao"), root));
                return temAcerto ? cb.exists(subquery) : cb.not(cb.exists(subquery));
            };
            spec = SpecificationUtils.and(spec, temAcertoSpec);
        }

        if (confirmada != null) {
            Specification<Consignacao> confirmadaSpec = (root, query, cb) -> cb.equal(root.get("confirmada"), confirmada);
            spec = SpecificationUtils.and(spec, confirmadaSpec);
        }

        if (dataEntregaInicio != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> SpecificationUtils.apartirDe(cb, root.get("dataEntrega"), dataEntregaInicio));
        }
        if (dataEntregaFim != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> SpecificationUtils.ate(cb, root.get("dataEntrega"), dataEntregaFim));
        }

        if (dataPreAcertoInicio != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> SpecificationUtils.apartirDe(cb, root.get("dataPreAcertoConsignacao"), dataPreAcertoInicio));
        }
        if (dataPreAcertoFim != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> SpecificationUtils.ate(cb, root.get("dataPreAcertoConsignacao"), dataPreAcertoFim));
        }

        if (dataAcertoInicio != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> SpecificationUtils.apartirDe(cb, root.get("dataAcertoConsignacao"), dataAcertoInicio));
        }
        if (dataAcertoFim != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> SpecificationUtils.ate(cb, root.get("dataAcertoConsignacao"), dataAcertoFim));
        }

        return repository.findAll(spec, pageable);
    }

    // versao enxuta pra listagem: nao toca em consignacao.produtos (evita carregar
    // produto/joia/variacao/categoria/fornecedor/ncm inteiros por linha, que é o que deixava a
    // listagem lenta) - os agregados (itensCount, quantidadeTotal, valorTotal, quantidadePorCategoria)
    // vêm de duas queries de GROUP BY à parte, batendo só nos ids da página atual
    public Page<ConsignacaoResumoDTO> findAllResumo(String busca, ModoBusca modoBusca, UUID clienteId, Boolean temCliente, Boolean temAcerto, Boolean confirmada,
                                                     LocalDate dataEntregaInicio, LocalDate dataEntregaFim,
                                                     LocalDate dataPreAcertoInicio, LocalDate dataPreAcertoFim,
                                                     LocalDate dataAcertoInicio, LocalDate dataAcertoFim,
                                                     Pageable pageable){
        Page<Consignacao> pagina = findAll(busca, modoBusca, clienteId, temCliente, temAcerto, confirmada,
                dataEntregaInicio, dataEntregaFim, dataPreAcertoInicio, dataPreAcertoFim, dataAcertoInicio, dataAcertoFim, pageable);

        List<Long> ids = pagina.getContent().stream().map(Consignacao::getId).toList();

        Map<Long, ConsignacaoTotaisProjecao> totaisPorId = new HashMap<>();
        Map<Long, List<QuantidadePorCategoriaDTO>> categoriasPorId = new HashMap<>();
        java.util.Set<Long> comAcertoConfirmado = new java.util.HashSet<>();

        if (!ids.isEmpty()) {
            for (ConsignacaoTotaisProjecao totais : consignacaoProdutoRepository.buscarTotais(ids)) {
                totaisPorId.put(totais.getConsignacaoId(), totais);
            }
            for (CategoriaQuantidadeProjecao linha : consignacaoProdutoRepository.buscarQuantidadePorCategoria(ids)) {
                categoriasPorId
                        .computeIfAbsent(linha.getConsignacaoId(), id -> new ArrayList<>())
                        .add(new QuantidadePorCategoriaDTO(linha.getCategoria(), linha.getQuantidade()));
            }
            comAcertoConfirmado.addAll(acertoConsignacaoRepository.findConsignacaoIdsComAcertoConfirmado(ids));
        }

        return pagina.map(consignacao -> toResumoDTO(consignacao, totaisPorId.get(consignacao.getId()),
                categoriasPorId.get(consignacao.getId()), comAcertoConfirmado.contains(consignacao.getId())));
    }

    private ConsignacaoResumoDTO toResumoDTO(Consignacao consignacao, ConsignacaoTotaisProjecao totais, List<QuantidadePorCategoriaDTO> quantidadePorCategoria, boolean acertoFinalizado){
        ConsignacaoResumoDTO dto = new ConsignacaoResumoDTO();
        dto.setId(consignacao.getId());
        dto.setDescricao(consignacao.getDescricao());
        dto.setCliente(consignacao.getCliente() != null
                ? new ClienteResumoDTO(consignacao.getCliente().getCodigo(), consignacao.getCliente().getNome())
                : null);
        dto.setMostruario(consignacao.getMostruario());
        dto.setDataEntrega(consignacao.getDataEntrega());
        dto.setDataPreAcertoConsignacao(consignacao.getDataPreAcertoConsignacao());
        dto.setDataAcertoConsignacao(consignacao.getDataAcertoConsignacao());
        dto.setConfirmada(consignacao.getConfirmada());
        dto.setAcertoFinalizado(acertoFinalizado);
        dto.setItensCount(totais != null ? totais.getItensCount().intValue() : 0);
        dto.setQuantidadeTotal(totais != null ? totais.getQuantidadeTotal() : 0);
        dto.setValorTotal(totais != null ? totais.getValorTotal() : java.math.BigDecimal.ZERO);
        dto.setQuantidadePorCategoria(quantidadePorCategoria != null ? quantidadePorCategoria : List.of());
        dto.setCreatedAt(consignacao.getCreatedAt());
        dto.setUpdatedAt(consignacao.getUpdatedAt());
        dto.setCreatedBy(usuarioResumoMapper.toResumoDTO(consignacao.getCreatedBy()));
        dto.setUpdatedBy(usuarioResumoMapper.toResumoDTO(consignacao.getUpdatedBy()));
        return dto;
    }

    private Specification<Consignacao> comBusca(String busca, ModoBusca modoBusca){
        if (busca == null || busca.isBlank()) return null;
        Long comoNumero = SpecificationUtils.comoLong(busca);
        return (root, query, cb) -> {
            var cliente = root.join("cliente", JoinType.LEFT);
            var mostruario = root.join("mostruario", JoinType.LEFT);
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>(List.of(
                    SpecificationUtils.likeIgnoreCase(cb, root.get("descricao"), busca, modoBusca),
                    SpecificationUtils.likeIgnoreCase(cb, cliente.get("codigo"), busca, modoBusca),
                    SpecificationUtils.likeIgnoreCase(cb, cliente.get("nome"), busca, modoBusca)
            ));
            if (comoNumero != null) {
                predicates.add(cb.equal(root.get("id"), comoNumero));
                predicates.add(cb.equal(mostruario.get("id"), comoNumero));
            }
            return cb.or(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    public Consignacao findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Consignação não encontrada. id: " + id));
    }

    private void preencherItem(ConsignacaoProduto produto, ConsignacaoProdutoRequestDTO produtoRequest, TipoItem tipo){
        validation.validarItem(tipo, produtoRequest.getProdutoId(), produtoRequest.getJoiaId(), produtoRequest.getProdutoVariacaoId());

        if (tipo == TipoItem.JOIA){
            produto.setJoia(joiaService.findByIdAndAtivo(produtoRequest.getJoiaId()));
            produto.setProduto(null);
            produto.setProdutoVariacao(null);
        } else {
            var produtoEncontrado = produtoService.findByIdAndAtivo(produtoRequest.getProdutoId());
            produto.setProduto(produtoEncontrado);
            produto.setJoia(null);
            produto.setProdutoVariacao(resolverProdutoVariacao(produtoRequest.getProdutoVariacaoId(), produtoEncontrado.getId()));
        }
    }

    private ProdutoVariacao resolverProdutoVariacao(Long produtoVariacaoId, UUID produtoId){
        if (produtoVariacaoId == null) return null;
        ProdutoVariacao variacao = produtoService.findVariacaoById(produtoVariacaoId);
        if (!variacao.getProduto().getId().equals(produtoId)) {
            throw new ItemInvalidoException("A combinação informada não pertence ao produto selecionado");
        }
        return variacao;
    }

    @Transactional
    public Consignacao create(ConsignacaoRequestDTO request){
        Consignacao consignacao = new Consignacao();

        if (!validation.clienteNovo(request.getClienteId())){
            consignacao.setCliente(clienteService.findByIdAndAtivo(request.getClienteId()));
        } else {
            consignacao.setCliente(null);
        }

        consignacao.setMostruario(mostruarioService.findById(request.getMostruarioId()));
        consignacao.setDescricao(request.getDescricao());
        consignacao.setTipo(request.getTipo());
        consignacao.setDataEntrega(request.getDataEntrega());
        consignacao.setDataPreAcertoConsignacao(request.getDataPreAcertoConsignacao());
        consignacao.setDataAcertoConsignacao(request.getDataAcertoConsignacao());

        for (ConsignacaoProdutoRequestDTO produtoRequest : request.getProdutos()){
            ConsignacaoProduto produto = new ConsignacaoProduto();

            preencherItem(produto, produtoRequest, request.getTipo());
            produto.setQuantidade(produtoRequest.getQuantidade());
            produto.setPreco(produtoRequest.getPreco());

            consignacao.adicionarProduto(produto);
        }

        return repository.save(consignacao);
    }

    @Transactional
    public Consignacao update(ConsignacaoRequestDTO request){
        Consignacao consignacaoUpdate = this.findById(request.getId());

        if (Boolean.TRUE.equals(consignacaoUpdate.getConfirmada())) {
            throw new ObjetoEmUsoException("Consignação confirmada não pode ser editada. id: " + consignacaoUpdate.getId());
        }

        if (!validation.clienteNovo(request.getClienteId())){
            consignacaoUpdate.setCliente(clienteService.findByIdAndAtivo(request.getClienteId()));
        } else {
            consignacaoUpdate.setCliente(null);
        }

        consignacaoUpdate.setMostruario(mostruarioService.findById(request.getMostruarioId()));
        consignacaoUpdate.setDescricao(request.getDescricao());
        consignacaoUpdate.setTipo(request.getTipo());
        consignacaoUpdate.setDataEntrega(request.getDataEntrega());
        consignacaoUpdate.setDataPreAcertoConsignacao(request.getDataPreAcertoConsignacao());
        consignacaoUpdate.setDataAcertoConsignacao(request.getDataAcertoConsignacao());

        List<ConsignacaoProduto> produtosAtuais = new ArrayList<>(consignacaoUpdate.getProdutos());

        for (ConsignacaoProdutoRequestDTO produtoRequest : request.getProdutos()){
            ConsignacaoProduto produtoExistente = null;

            for (ConsignacaoProduto produto : consignacaoUpdate.getProdutos()){
                Long variacaoAtualId = produto.getProdutoVariacao() != null ? produto.getProdutoVariacao().getId() : null;
                boolean mesmaVariacao = Objects.equals(variacaoAtualId, produtoRequest.getProdutoVariacaoId());
                boolean mesmoProduto = produto.getProduto() != null && produto.getProduto().getId().equals(produtoRequest.getProdutoId()) && mesmaVariacao;
                boolean mesmaJoia = produto.getJoia() != null && produto.getJoia().getId().equals(produtoRequest.getJoiaId());
                if (mesmoProduto || mesmaJoia) {
                    produtoExistente = produto;
                    break;
                }
            }

            if(produtoExistente != null){

                if (!produtoExistente.getQuantidade().equals(produtoRequest.getQuantidade())) {
                    produtoExistente.setQuantidade(produtoRequest.getQuantidade());
                }

                if (produtoExistente.getPreco().compareTo(produtoRequest.getPreco()) != 0) {
                    produtoExistente.setPreco(produtoRequest.getPreco());
                }

                produtosAtuais.remove(produtoExistente);
            } else {
                ConsignacaoProduto novoProduto = new ConsignacaoProduto();

                preencherItem(novoProduto, produtoRequest, request.getTipo());
                novoProduto.setQuantidade(produtoRequest.getQuantidade());
                novoProduto.setPreco(produtoRequest.getPreco());
                consignacaoUpdate.adicionarProduto(novoProduto);
            }
        }

        for (ConsignacaoProduto removerProduto : produtosAtuais) {
            consignacaoUpdate.removerProduto(removerProduto);
        }

        // Sem isso, uma edição que só mexe nos produtos não deixa a consignação
        // marcada como "suja" pro Hibernate, e updatedAt/updatedBy nunca mudam - ver
        // o comentário em ConsignacaoRepository.tocarAtualizacao.
        LocalDateTime agora = LocalDateTime.now();
        var usuarioAtual = usuarioAuditorAware.getCurrentAuditor().orElse(null);
        repository.tocarAtualizacao(consignacaoUpdate.getId(), agora, usuarioAtual);
        consignacaoUpdate.setUpdatedAt(agora);
        consignacaoUpdate.setUpdatedBy(usuarioAtual);

        return consignacaoUpdate;
    }

    @Transactional
    public Consignacao confirmar(Long id){
        Consignacao consignacao = this.findById(id);

        if (Boolean.TRUE.equals(consignacao.getConfirmada())) {
            throw new ItemInvalidoException("Consignação já está confirmada. id: " + id);
        }

        if (consignacao.getCliente() == null) {
            throw new ItemInvalidoException("Consignação sem cliente não pode ser confirmada. id: " + id);
        }

        consignacao.setConfirmada(true);
        return consignacao;
    }

    private String categoriaDe(ConsignacaoProduto item){
        if (item.getProduto() != null){
            return item.getProduto().getCategoria() != null ? item.getProduto().getCategoria().getNome() : "";
        }
        return item.getJoia().getCategoria() != null ? item.getJoia().getCategoria().getNome() : "";
    }

    private int indiceCategoria(String categoria, List<String> ordem){
        for (int i = 0; i < ordem.size(); i++){
            if (ordem.get(i).equalsIgnoreCase(categoria.trim())) return i;
        }
        return Integer.MAX_VALUE;
    }

    private long codigoNumerico(String codigo){
        if (codigo == null) return Long.MAX_VALUE;
        String digitos = codigo.replaceAll("\\D", "");
        if (digitos.isBlank()) return Long.MAX_VALUE;
        try {
            return Long.parseLong(digitos);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    private List<ConsignacaoProduto> ordenarProdutos(Consignacao consignacao){
        List<ConsignacaoProduto> produtos = new ArrayList<>(consignacao.getProdutos());
        boolean joia = consignacao.getTipo() == TipoItem.JOIA;
        List<String> ordemCategorias = joia ? ORDEM_CATEGORIAS_JOIA : ORDEM_CATEGORIAS_PADRAO;

        Comparator<ConsignacaoProduto> porCategoria = Comparator
                .comparingInt((ConsignacaoProduto item) -> indiceCategoria(categoriaDe(item), ordemCategorias))
                .thenComparing(item -> categoriaDe(item).toLowerCase());

        Comparator<ConsignacaoProduto> porItem = joia
                ? Comparator.comparingLong(item -> codigoNumerico(item.getJoia().getCodigo()))
                : Comparator.comparingLong(item -> codigoNumerico(item.getProdutoVariacao() != null ? item.getProdutoVariacao().getSku() : item.getProduto().getSku()));

        produtos.sort(porCategoria.thenComparing(porItem));
        return produtos;
    }

    public List<Map<String, ?>> dataConsignacao(Consignacao consignacao){
        List<Map<String, ?>> linhas = new ArrayList<>();
        Cliente cliente = consignacao.getCliente();
        Endereco endereco = cliente != null ? cliente.getEnderecoPrincipal() : null;

        for (ConsignacaoProduto consignacaoProduto : ordenarProdutos(consignacao)){
            Map<String, Object> linha = new HashMap<>();
            Produto produto = consignacaoProduto.getProduto();
            Joia joia = consignacaoProduto.getJoia();
            ProdutoVariacao variacao = consignacaoProduto.getProdutoVariacao();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

            String codigo = variacao != null ? variacao.getSku() : (produto != null ? produto.getSku() : joia.getCodigo());
            String nome = produto != null ? produto.getNome() : joia.getNome();
            String categoria = produto != null ? (produto.getCategoria() != null ? produto.getCategoria().getNome() : null) : (joia.getCategoria() != null ? joia.getCategoria().getNome() : null);

            linha.put("id", consignacao.getId() != null ? consignacao.getId().intValue() : null);
            linha.put("dataCricacao", consignacao.getCreatedAt() != null ? Date.valueOf(consignacao.getCreatedAt().toLocalDate()) : null);
            linha.put("dataEntrega", consignacao.getDataEntrega() != null ? consignacao.getDataEntrega().format(formatter) : null);
            linha.put("dataPreAcerto", consignacao.getDataPreAcertoConsignacao()!= null ? consignacao.getDataPreAcertoConsignacao().format(formatter) : null);
            linha.put("dataAcerto", consignacao.getDataAcertoConsignacao()!= null ? consignacao.getDataAcertoConsignacao().format(formatter) : null);
            linha.put("valorTotal", consignacao.getValorTotal() != null ? consignacao.getValorTotal().doubleValue() : null);
            linha.put("qtdProdutos", consignacao.getQuantidadeTotal());
            linha.put("kit", consignacao.getMostruario() != null ? consignacao.getMostruario().getDescricao() : "");
            linha.put("descricao", consignacao.getDescricao() != null ? consignacao.getDescricao() : "");

            linha.put("codProduto", codigo);
            linha.put("nomeProduto", nome);
            linha.put("valorProduto", consignacaoProduto.getPreco() != null ? consignacaoProduto.getPreco().doubleValue() : null);
            linha.put("qtdProduto", consignacaoProduto.getQuantidade());
            linha.put("tipoProduto", categoria);

            if (cliente != null){
                linha.put("codCliente", cliente.getCodigo());
                linha.put("nomeCliente", cliente.getNome());
                linha.put("cpf_cnpj", cliente.getCpf());
                linha.put("documento", cliente.getDocumento());
                linha.put("celular", cliente.getCelular());
                linha.put("telefone", cliente.getTelefone());
            }

            if (endereco != null){
                linha.put("cep", endereco.getCep());
                linha.put("logradouro", endereco.getLogradouro());
                linha.put("complemento", endereco.getComplemento() != null ? endereco.getComplemento() : "");
                linha.put("nuCasa", endereco.getNumero());
                linha.put("municipio", endereco.getMunicipio());
                linha.put("bairro", endereco.getBairro());
                linha.put("uf", endereco.getUf());
            }

            linhas.add(linha);
        }

        return linhas;
    }

    public byte[] gerarRelatorio(Long id, TipoRelatorioConsignacao tipo){
        try {
            Consignacao consignacao = this.findById(id);
            JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource(dataConsignacao(consignacao));
            boolean semCliente = consignacao.getCliente() == null;
            Map<String, Object> params = new HashMap<>();

            String relPath;
            if (tipo == TipoRelatorioConsignacao.JOIAS) {
                params.put("cabecalho", Files.newInputStream(Path.of(relatorioPath+"/cabecalhoBeijo.png")));
                params.put("rodape", Files.newInputStream(Path.of(relatorioPath+"/rodapeBeijo.png")));
                relPath = "/reports/consignacao/relConsigJoiasNovoBeijo.jasper";
            } else {
                params.put("cabecalho", Files.newInputStream(Path.of(relatorioPath+"/cabecalhoCharme.png")));
                params.put("rodape", Files.newInputStream(Path.of(relatorioPath+"/rodapeCharme.png")));
                relPath = semCliente
                        ? "/reports/consignacao/relConsigJoiasNovoCharme.jasper"
                        : "/reports/consignacao/relConsigJoiasCharme.jasper";
            }

            InputStream jasperStream = getClass().getResourceAsStream(relPath);
            JasperPrint print = JasperFillManager.fillReport(jasperStream, params, dataSource);
            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar relatório de consignação", e);
        }
    }
}
