package com.manager.app.preacerto;

import com.manager.app.cliente.Cliente;
import com.manager.app.cliente.ClienteService;
import com.manager.app.consignacao.Consignacao;
import com.manager.app.consignacao.ConsignacaoProduto;
import com.manager.app.consignacao.ConsignacaoRepository;
import com.manager.app.consignacao.ConsignacaoValidation;
import com.manager.app.joia.Joia;
import com.manager.app.joia.JoiaService;
import com.manager.app.consignacao.dto.ClienteResumoDTO;
import com.manager.app.consignacao.dto.QuantidadePorCategoriaDTO;
import com.manager.app.preacerto.dto.PreAcertoConsignacaoProdutoRequestDTO;
import com.manager.app.preacerto.dto.PreAcertoConsignacaoRequestDTO;
import com.manager.app.preacerto.dto.PreAcertoConsignacaoResumoDTO;
import com.manager.app.produto.Produto;
import com.manager.app.produto.ProdutoService;
import com.manager.app.produto.ProdutoVariacao;
import com.manager.app.shared.TipoItem;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.ModoBusca;
import com.manager.app.shared.specification.SpecificationUtils;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.usuario.UsuarioResumoMapper;
import jakarta.persistence.criteria.JoinType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PreAcertoConsignacaoService {

    private final PreAcertoConsignacaoRepository repository;
    private final PreAcertoConsignacaoProdutoRepository preAcertoConsignacaoProdutoRepository;
    private final ConsignacaoRepository consignacaoRepository;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final JoiaService joiaService;
    private final ConsignacaoValidation consignacaoValidation;
    private final UsuarioResumoMapper usuarioResumoMapper;
    private final UsuarioAuditorAware usuarioAuditorAware;

    public Page<PreAcertoConsignacao> findAll(String busca, ModoBusca modoBusca, Long consignacaoId,
                                               LocalDate dataEntregaInicio, LocalDate dataEntregaFim,
                                               LocalDate dataPreAcertoInicio, LocalDate dataPreAcertoFim,
                                               LocalDate dataAcertoInicio, LocalDate dataAcertoFim,
                                               Pageable pageable){
        Specification<PreAcertoConsignacao> spec = comBusca(busca, modoBusca);

        if (consignacaoId != null) {
            Specification<PreAcertoConsignacao> consignacaoSpec = (root, query, cb) ->
                    cb.equal(root.get("consignacao").get("id"), consignacaoId);
            spec = SpecificationUtils.and(spec, consignacaoSpec);
        }

        // as datas vivem na Consignação referenciada, não no Pré-acerto - INNER JOIN explícito porque
        // pré-acerto pode não ter Consignação de referência (criado direto pra um Cliente), e esses
        // devem ficar de fora quando esses filtros são usados (não têm data nenhuma pra comparar)
        if (dataEntregaInicio != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) ->
                    SpecificationUtils.apartirDe(cb, root.join("consignacao", JoinType.INNER).get("dataEntrega"), dataEntregaInicio));
        }
        if (dataEntregaFim != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) ->
                    SpecificationUtils.ate(cb, root.join("consignacao", JoinType.INNER).get("dataEntrega"), dataEntregaFim));
        }

        if (dataPreAcertoInicio != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) ->
                    SpecificationUtils.apartirDe(cb, root.join("consignacao", JoinType.INNER).get("dataPreAcertoConsignacao"), dataPreAcertoInicio));
        }
        if (dataPreAcertoFim != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) ->
                    SpecificationUtils.ate(cb, root.join("consignacao", JoinType.INNER).get("dataPreAcertoConsignacao"), dataPreAcertoFim));
        }

        if (dataAcertoInicio != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) ->
                    SpecificationUtils.apartirDe(cb, root.join("consignacao", JoinType.INNER).get("dataAcertoConsignacao"), dataAcertoInicio));
        }
        if (dataAcertoFim != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) ->
                    SpecificationUtils.ate(cb, root.join("consignacao", JoinType.INNER).get("dataAcertoConsignacao"), dataAcertoFim));
        }

        return repository.findAll(spec, pageable);
    }

    // versao enxuta pra listagem, no mesmo formato de ConsignacaoService.findAllResumo: não toca em
    // preAcerto.produtos, os agregados (itensCount, quantidadeTotal, valorTotal) vêm de uma query de
    // GROUP BY à parte, batendo só nos ids da página atual
    public Page<PreAcertoConsignacaoResumoDTO> findAllResumo(String busca, ModoBusca modoBusca, Long consignacaoId,
                                                              LocalDate dataEntregaInicio, LocalDate dataEntregaFim,
                                                              LocalDate dataPreAcertoInicio, LocalDate dataPreAcertoFim,
                                                              LocalDate dataAcertoInicio, LocalDate dataAcertoFim,
                                                              Pageable pageable){
        Page<PreAcertoConsignacao> pagina = findAll(busca, modoBusca, consignacaoId,
                dataEntregaInicio, dataEntregaFim, dataPreAcertoInicio, dataPreAcertoFim, dataAcertoInicio, dataAcertoFim, pageable);

        List<Long> ids = pagina.getContent().stream().map(PreAcertoConsignacao::getId).toList();

        Map<Long, PreAcertoConsignacaoTotaisProjecao> totaisPorId = new HashMap<>();
        Map<Long, List<QuantidadePorCategoriaDTO>> categoriasPorId = new HashMap<>();

        if (!ids.isEmpty()) {
            for (PreAcertoConsignacaoTotaisProjecao totais : preAcertoConsignacaoProdutoRepository.buscarTotais(ids)) {
                totaisPorId.put(totais.getPreAcertoId(), totais);
            }
            for (PreAcertoCategoriaQuantidadeProjecao linha : preAcertoConsignacaoProdutoRepository.buscarQuantidadePorCategoria(ids)) {
                categoriasPorId
                        .computeIfAbsent(linha.getPreAcertoId(), id -> new ArrayList<>())
                        .add(new QuantidadePorCategoriaDTO(linha.getCategoria(), linha.getQuantidade()));
            }
        }

        return pagina.map(preAcerto -> toResumoDTO(preAcerto, totaisPorId.get(preAcerto.getId()), categoriasPorId.get(preAcerto.getId())));
    }

    private PreAcertoConsignacaoResumoDTO toResumoDTO(PreAcertoConsignacao preAcerto, PreAcertoConsignacaoTotaisProjecao totais, List<QuantidadePorCategoriaDTO> quantidadePorCategoria){
        PreAcertoConsignacaoResumoDTO dto = new PreAcertoConsignacaoResumoDTO();
        dto.setId(preAcerto.getId());
        dto.setConsignacaoId(preAcerto.getConsignacao() != null ? preAcerto.getConsignacao().getId() : null);
        dto.setCliente(preAcerto.getCliente() != null
                ? new ClienteResumoDTO(preAcerto.getCliente().getCodigo(), preAcerto.getCliente().getNome())
                : null);
        dto.setObservacoes(preAcerto.getObservacoes());
        dto.setItensCount(totais != null ? totais.getItensCount().intValue() : 0);
        BigDecimal valorTotal = totais != null ? totais.getValorTotal() : BigDecimal.ZERO;
        dto.setQuantidadeTotal(totais != null ? totais.getQuantidadeTotal() : 0);
        dto.setValorTotal(valorTotal);
        dto.setDesconto(preAcerto.getDesconto());
        BigDecimal descontoAplicado = preAcerto.getDesconto() != null ? preAcerto.getDesconto() : BigDecimal.ZERO;
        dto.setValorTotalComDesconto(valorTotal.subtract(descontoAplicado));
        dto.setQuantidadePorCategoria(quantidadePorCategoria != null ? quantidadePorCategoria : List.of());
        dto.setCreatedAt(preAcerto.getCreatedAt());
        dto.setUpdatedAt(preAcerto.getUpdatedAt());
        dto.setCreatedBy(usuarioResumoMapper.toResumoDTO(preAcerto.getCreatedBy()));
        dto.setUpdatedBy(usuarioResumoMapper.toResumoDTO(preAcerto.getUpdatedBy()));
        return dto;
    }

    private Specification<PreAcertoConsignacao> comBusca(String busca, ModoBusca modoBusca){
        if (busca == null || busca.isBlank()) return null;
        Long comoNumero = SpecificationUtils.comoLong(busca);
        return (root, query, cb) -> {
            var cliente = root.join("cliente", JoinType.LEFT);
            var consignacao = root.join("consignacao", JoinType.LEFT);
            var mostruario = consignacao.join("mostruario", JoinType.LEFT);
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>(List.of(
                    SpecificationUtils.likeIgnoreCase(cb, root.get("observacoes"), busca, modoBusca),
                    SpecificationUtils.likeIgnoreCase(cb, cliente.get("codigo"), busca, modoBusca),
                    SpecificationUtils.likeIgnoreCase(cb, cliente.get("nome"), busca, modoBusca)
            ));
            if (comoNumero != null) {
                predicates.add(cb.equal(root.get("id"), comoNumero));
                predicates.add(cb.equal(consignacao.get("id"), comoNumero));
                predicates.add(cb.equal(mostruario.get("id"), comoNumero));
            }
            return cb.or(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    public PreAcertoConsignacao findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Pré-acerto de consignação não encontrado. id: " + id));
    }

    private void validarConsignacaoDisponivel(Long consignacaoId, Long idAtual){
        boolean emUso = idAtual == null
                ? repository.existsByConsignacaoId(consignacaoId)
                : repository.existsByConsignacaoIdAndIdNot(consignacaoId, idAtual);
        if (emUso) {
            throw new ObjetoExistenteException("Esta Consignação já possui um Pré-acerto. consignacaoId: " + consignacaoId);
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

    private BigDecimal resolverPreco(Consignacao consignacao, Produto produto, ProdutoVariacao produtoVariacao, Joia joia, BigDecimal precoRequest){
        if (consignacao != null) {
            for (ConsignacaoProduto item : consignacao.getProdutos()) {
                Long variacaoItemId = item.getProdutoVariacao() != null ? item.getProdutoVariacao().getId() : null;
                Long variacaoNovaId = produtoVariacao != null ? produtoVariacao.getId() : null;
                boolean mesmoProduto = produto != null && item.getProduto() != null
                        && item.getProduto().getId().equals(produto.getId())
                        && Objects.equals(variacaoItemId, variacaoNovaId);
                boolean mesmaJoia = joia != null && item.getJoia() != null && item.getJoia().getId().equals(joia.getId());
                if (mesmoProduto || mesmaJoia) {
                    return item.getPreco();
                }
            }
        }

        if (precoRequest == null) {
            throw new ItemInvalidoException("Preço é obrigatório para produtos que não estão na Consignação de referência");
        }
        return precoRequest;
    }

    private void preencherItem(PreAcertoConsignacaoProduto item, PreAcertoConsignacaoProdutoRequestDTO produtoRequest, Consignacao consignacao, TipoItem tipo){
        consignacaoValidation.validarItem(tipo, produtoRequest.getProdutoId(), produtoRequest.getJoiaId(), produtoRequest.getProdutoVariacaoId());

        Produto produto = null;
        ProdutoVariacao produtoVariacao = null;
        Joia joia = null;

        if (tipo == TipoItem.JOIA) {
            joia = joiaService.findByIdAndAtivo(produtoRequest.getJoiaId());
        } else {
            produto = produtoService.findByIdAndAtivo(produtoRequest.getProdutoId());
            produtoVariacao = resolverProdutoVariacao(produtoRequest.getProdutoVariacaoId(), produto.getId());
        }

        item.setProduto(produto);
        item.setProdutoVariacao(produtoVariacao);
        item.setJoia(joia);
        item.setQuantidade(produtoRequest.getQuantidade());
        item.setPreco(resolverPreco(consignacao, produto, produtoVariacao, joia, produtoRequest.getPreco()));
    }

    private Consignacao resolverConsignacao(Long consignacaoId){
        if (consignacaoId == null) return null;
        return consignacaoRepository.findById(consignacaoId)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Consignação não encontrada. id: " + consignacaoId));
    }

    private TipoItem tipoDe(Consignacao consignacao){
        return consignacao != null ? consignacao.getTipo() : TipoItem.PRODUTO;
    }

    @Transactional
    public PreAcertoConsignacao create(PreAcertoConsignacaoRequestDTO request){
        PreAcertoConsignacao preAcerto = new PreAcertoConsignacao();

        Consignacao consignacao = resolverConsignacao(request.getConsignacaoId());
        Cliente cliente;

        if (consignacao != null) {
            validarConsignacaoDisponivel(consignacao.getId(), null);
            cliente = consignacao.getCliente();
        } else {
            if (request.getClienteId() == null) {
                throw new ItemInvalidoException("clienteId é obrigatório quando não há Consignação de referência");
            }
            cliente = clienteService.findByIdAndAtivo(request.getClienteId());
        }

        preAcerto.setConsignacao(consignacao);
        preAcerto.setCliente(cliente);
        preAcerto.setObservacoes(request.getObservacoes());
        preAcerto.setDesconto(request.getDesconto());

        TipoItem tipo = tipoDe(consignacao);

        for (PreAcertoConsignacaoProdutoRequestDTO produtoRequest : request.getProdutos()) {
            PreAcertoConsignacaoProduto item = new PreAcertoConsignacaoProduto();
            preencherItem(item, produtoRequest, consignacao, tipo);
            preAcerto.adicionarProduto(item);
        }

        return repository.save(preAcerto);
    }

    @Transactional
    public PreAcertoConsignacao update(PreAcertoConsignacaoRequestDTO request){
        PreAcertoConsignacao preAcertoUpdate = this.findById(request.getId());

        Consignacao consignacao = resolverConsignacao(request.getConsignacaoId());
        Cliente cliente;

        if (consignacao != null) {
            validarConsignacaoDisponivel(consignacao.getId(), preAcertoUpdate.getId());
            cliente = consignacao.getCliente();
        } else {
            if (request.getClienteId() == null) {
                throw new ItemInvalidoException("clienteId é obrigatório quando não há Consignação de referência");
            }
            cliente = clienteService.findByIdAndAtivo(request.getClienteId());
        }

        preAcertoUpdate.setConsignacao(consignacao);
        preAcertoUpdate.setCliente(cliente);
        preAcertoUpdate.setObservacoes(request.getObservacoes());
        preAcertoUpdate.setDesconto(request.getDesconto());

        TipoItem tipo = tipoDe(consignacao);

        List<PreAcertoConsignacaoProduto> produtosAtuais = new ArrayList<>(preAcertoUpdate.getProdutos());

        for (PreAcertoConsignacaoProdutoRequestDTO produtoRequest : request.getProdutos()) {
            PreAcertoConsignacaoProduto produtoExistente = null;

            for (PreAcertoConsignacaoProduto produto : preAcertoUpdate.getProdutos()) {
                Long variacaoAtualId = produto.getProdutoVariacao() != null ? produto.getProdutoVariacao().getId() : null;
                boolean mesmaVariacao = Objects.equals(variacaoAtualId, produtoRequest.getProdutoVariacaoId());
                boolean mesmoProduto = produto.getProduto() != null && produto.getProduto().getId().equals(produtoRequest.getProdutoId()) && mesmaVariacao;
                boolean mesmaJoia = produto.getJoia() != null && produto.getJoia().getId().equals(produtoRequest.getJoiaId());
                if (mesmoProduto || mesmaJoia) {
                    produtoExistente = produto;
                    break;
                }
            }

            if (produtoExistente != null) {
                preencherItem(produtoExistente, produtoRequest, consignacao, tipo);
                produtosAtuais.remove(produtoExistente);
            } else {
                PreAcertoConsignacaoProduto novoProduto = new PreAcertoConsignacaoProduto();
                preencherItem(novoProduto, produtoRequest, consignacao, tipo);
                preAcertoUpdate.adicionarProduto(novoProduto);
            }
        }

        for (PreAcertoConsignacaoProduto removerProduto : produtosAtuais) {
            preAcertoUpdate.removerProduto(removerProduto);
        }

        // Sem isso, uma edição que só mexe nos produtos não deixa o pré-acerto
        // marcado como "sujo" pro Hibernate, e updatedAt/updatedBy nunca mudam - ver
        // o comentário em PreAcertoConsignacaoRepository.tocarAtualizacao.
        LocalDateTime agora = LocalDateTime.now();
        var usuarioAtual = usuarioAuditorAware.getCurrentAuditor().orElse(null);
        repository.tocarAtualizacao(preAcertoUpdate.getId(), agora, usuarioAtual);
        preAcertoUpdate.setUpdatedAt(agora);
        preAcertoUpdate.setUpdatedBy(usuarioAtual);

        return preAcertoUpdate;
    }
}
