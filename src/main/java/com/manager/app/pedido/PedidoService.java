package com.manager.app.pedido;

import com.manager.app.pedido.dto.PedidoProdutoRequestDTO;
import com.manager.app.pedido.dto.PedidoRequestDTO;
import com.manager.app.pedido.dto.PedidoContagemDTO;
import com.manager.app.pedido.dto.PedidoResumoDTO;
import com.manager.app.pedido.dto.PedidoStatusLoteRequestDTO;
import com.manager.app.pedido.dto.PedidoStatusLoteResultadoDTO;
import com.manager.app.pedido.dto.PedidoStatusRequestDTO;
import com.manager.app.shared.ModoBusca;
import com.manager.app.shared.specification.SpecificationUtils;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import com.manager.app.produto.ProdutoVariacao;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.TipoItem;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.manager.app.grupo.GrupoService;
import com.manager.app.mostruario.Mostruario;
import com.manager.app.mostruario.MostruarioService;
import com.manager.app.cliente.ClienteService;
import com.manager.app.joia.JoiaService;
import com.manager.app.produto.ProdutoService;
import com.manager.app.usuario.UsuarioResumoMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository repository;
    private final PedidoProdutoRepository pedidoProdutoRepository;
    private final TransactionTemplate transactionTemplate;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final JoiaService joiaService;
    private final GrupoService grupoService;
    private final MostruarioService mostruarioService;
    private final PedidoValidation validation;
    private final UsuarioResumoMapper usuarioResumoMapper;

    public Page<PedidoResumoDTO> findAllResumo(PedidoFiltro filtro, Pageable pageable){
        Page<Pedido> pagina = repository.findAll(montarSpec(filtro), pageable);

        List<Long> ids = pagina.getContent().stream().map(Pedido::getId).toList();
        Map<Long, PedidoTotaisProjecao> totaisPorId = new HashMap<>();
        if (!ids.isEmpty()) {
            for (PedidoTotaisProjecao totais : pedidoProdutoRepository.buscarTotais(ids)) {
                totaisPorId.put(totais.getPedidoId(), totais);
            }
        }

        return pagina.map(pedido -> toResumoDTO(pedido, totaisPorId.get(pedido.getId())));
    }

    public PedidoContagemDTO contar(PedidoFiltro filtro){
        Map<StatusPedido, Long> porStatus = new EnumMap<>(StatusPedido.class);
        Specification<Pedido> baseStatus = montarSpec(filtro.semStatus());
        for (StatusPedido status : StatusPedido.values()) {
            porStatus.put(status, repository.count(SpecificationUtils.and(baseStatus,
                    (root, query, cb) -> cb.equal(root.get("status"), status))));
        }

        Map<CategoriaPedido, Long> porCategoria = new EnumMap<>(CategoriaPedido.class);
        Specification<Pedido> baseCategoria = montarSpec(filtro.semCategoria());
        for (CategoriaPedido categoria : CategoriaPedido.values()) {
            porCategoria.put(categoria, repository.count(SpecificationUtils.and(baseCategoria,
                    (root, query, cb) -> cb.equal(root.get("categoria"), categoria))));
        }
        return new PedidoContagemDTO(porStatus, porCategoria);
    }

    private PedidoResumoDTO toResumoDTO(Pedido pedido, PedidoTotaisProjecao totais){
        PedidoResumoDTO dto = new PedidoResumoDTO();
        dto.setId(pedido.getId());
        if (pedido.getCliente() != null) {
            dto.setClienteId(pedido.getCliente().getId());
            dto.setClienteCodigo(pedido.getCliente().getCodigo());
            dto.setClienteNome(pedido.getCliente().getNome());
            var endereco = pedido.getCliente().getEnderecoPrincipal();
            dto.setCidade(endereco != null ? endereco.getMunicipio() : null);
        }
        if (pedido.getMostruario() != null) {
            dto.setMostruarioId(pedido.getMostruario().getId());
            dto.setMostruarioDescricao(pedido.getMostruario().getDescricao());
        }
        dto.setCategoria(pedido.getCategoria());
        dto.setStatus(pedido.getStatus());
        dto.setTipo(pedido.getTipo());
        dto.setImpresso(pedido.isImpresso());
        dto.setImpressoEm(pedido.getImpressoEm());
        dto.setDataPedido(pedido.getDataPedido());
        dto.setDataEntrega(pedido.getDataEntrega());
        dto.setObservacao(pedido.getObservacao());
        dto.setMotivoCancelamento(pedido.getMotivoCancelamento());
        dto.setItensCount(totais != null ? totais.getItensCount().intValue() : 0);
        dto.setQuantidadeTotal(totais != null ? totais.getQuantidadeTotal() : 0);
        dto.setValorTotal(totais != null ? totais.getValorTotal() : BigDecimal.ZERO);
        dto.setCreatedAt(pedido.getCreatedAt());
        dto.setUpdatedAt(pedido.getUpdatedAt());
        dto.setCreatedBy(usuarioResumoMapper.toResumoDTO(pedido.getCreatedBy()));
        dto.setUpdatedBy(usuarioResumoMapper.toResumoDTO(pedido.getUpdatedBy()));
        return dto;
    }

    private Specification<Pedido> montarSpec(PedidoFiltro f){
        Specification<Pedido> spec = null;

        if (f.busca() != null && !f.busca().isBlank()) {
            String busca = f.busca();
            ModoBusca modo = f.modoBusca();
            Long comoNumero = SpecificationUtils.comoLong(busca);
            spec = SpecificationUtils.and(spec, (root, query, cb) -> {
                var cliente = root.join("cliente", JoinType.LEFT);
                List<Predicate> predicates = new ArrayList<>(List.of(
                        SpecificationUtils.likeIgnoreCase(cb, cliente.get("codigo"), busca, modo),
                        SpecificationUtils.likeIgnoreCase(cb, cliente.get("nome"), busca, modo),
                        SpecificationUtils.likeIgnoreCase(cb, root.get("observacao"), busca, modo)
                ));
                if (comoNumero != null) {
                    predicates.add(cb.equal(root.get("id"), comoNumero));
                }
                return cb.or(predicates.toArray(new Predicate[0]));
            });
        }

        if (f.status() != null && !f.status().isEmpty()) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> root.get("status").in(f.status()));
        }
        if (f.categoria() != null && !f.categoria().isEmpty()) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> root.get("categoria").in(f.categoria()));
        }
        if (f.numero() != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> cb.equal(root.get("id"), f.numero()));
        }
        if (f.clienteCodigo() != null && !f.clienteCodigo().isBlank()) {
            spec = SpecificationUtils.and(spec, (root, query, cb) ->
                    SpecificationUtils.likeIgnoreCase(cb, root.join("cliente").get("codigo"), f.clienteCodigo(), f.modoBusca()));
        }
        if (f.clienteNome() != null && !f.clienteNome().isBlank()) {
            spec = SpecificationUtils.and(spec, (root, query, cb) ->
                    SpecificationUtils.likeIgnoreCase(cb, root.join("cliente").get("nome"), f.clienteNome(), f.modoBusca()));
        }
        if (f.cidade() != null && !f.cidade().isBlank()) {
            // só o endereço principal do cliente conta como "cidade" do pedido
            spec = SpecificationUtils.and(spec, (root, query, cb) -> {
                var endereco = root.join("cliente").join("enderecos");
                return cb.and(
                        cb.isTrue(endereco.get("principal")),
                        SpecificationUtils.likeIgnoreCase(cb, endereco.get("municipio"), f.cidade(), f.modoBusca()));
            });
        }
        if (f.dataEntregaInicio() != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> SpecificationUtils.apartirDe(cb, root.get("dataEntrega"), f.dataEntregaInicio()));
        }
        if (f.dataEntregaFim() != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> SpecificationUtils.ate(cb, root.get("dataEntrega"), f.dataEntregaFim()));
        }
        if (f.criadoInicio() != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> SpecificationUtils.criadoAPartirDe(cb, root.get("createdAt"), f.criadoInicio()));
        }
        if (f.criadoFim() != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> SpecificationUtils.criadoAte(cb, root.get("createdAt"), f.criadoFim()));
        }
        return spec;
    }

    public Pedido findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Pedido não encontrada. id: " + id));
    }

    private void preencherItem(PedidoProduto produto, PedidoProdutoRequestDTO produtoRequest, TipoItem tipo){
        validation.validarItem(tipo, produtoRequest.getProdutoId(), produtoRequest.getJoiaId(), produtoRequest.getProdutoVariacaoId());

        if (tipo == TipoItem.JOIA){
            produto.setDescricao(null);
            produto.setJoia(joiaService.findByIdAndAtivo(produtoRequest.getJoiaId()));
            produto.setProduto(null);
            produto.setProdutoVariacao(null);
            produto.setGrupo(null);
        } else if (!validation.produtoNovo(produtoRequest.getProdutoId())){
            produto.setDescricao(null);
            var produtoEncontrado = produtoService.findByIdAndAtivo(produtoRequest.getProdutoId());
            produto.setProduto(produtoEncontrado);
            produto.setJoia(null);
            produto.setProdutoVariacao(resolverProdutoVariacao(produtoRequest.getProdutoVariacaoId(), produtoEncontrado.getId()));
            produto.setGrupo(null);
        } else {
            produto.setDescricao(produtoRequest.getDescricao());
            produto.setProduto(null);
            produto.setJoia(null);
            produto.setProdutoVariacao(null);
            produto.setGrupo(produtoRequest.getGrupoId() != null
                    ? grupoService.findById(produtoRequest.getGrupoId())
                    : null);
        }
    }

    private ProdutoVariacao resolverProdutoVariacao(Long produtoVariacaoId, java.util.UUID produtoId){
        if (produtoVariacaoId == null) return null;
        ProdutoVariacao variacao = produtoService.findVariacaoById(produtoVariacaoId);
        if (!variacao.getProduto().getId().equals(produtoId)) {
            throw new ItemInvalidoException("A combinação informada não pertence ao produto selecionado");
        }
        return variacao;
    }

    private Mostruario resolverMostruario(Long mostruarioId){
        return mostruarioId == null ? null : mostruarioService.findById(mostruarioId);
    }

    @Transactional
    public Pedido create(PedidoRequestDTO request){
        Pedido pedido = new Pedido();

        pedido.setObservacao(request.getObservacao());
        pedido.setDataPedido(request.getDataPedido());
        pedido.setDataEntrega(request.getDataEntrega());
        pedido.setCategoria(request.getCategoria());
        pedido.setStatus(validation.statusInicial(request.getCategoria()));
        pedido.setTipo(request.getTipo());
        pedido.setCliente(clienteService.findByIdAndAtivo(request.getClienteId()));
        pedido.setMostruario(resolverMostruario(request.getMostruarioId()));

        for (PedidoProdutoRequestDTO produtoRequest : request.getProdutosRequest()){
            PedidoProduto produto = new PedidoProduto();

            preencherItem(produto, produtoRequest, request.getTipo());

            produto.setVariacao(produtoRequest.getVariacao());
            produto.setQuantidade(produtoRequest.getQuantidade());
            produto.setPreco(produtoRequest.getPreco());
            pedido.adicionarProduto(produto);
        }

        return repository.saveAndFlush(pedido);
    }

    @Transactional
    public Pedido update(PedidoRequestDTO request){
        Pedido pedidoUpdate = this.findById(request.getId());
        validation.validarEdicao(pedidoUpdate);

        if (pedidoUpdate.getCategoria() != request.getCategoria()) {
            throw new ItemInvalidoException("A categoria do pedido não pode ser alterada. id: " + pedidoUpdate.getId());
        }

        // editou, então o que foi impresso antes pode estar desatualizado
        pedidoUpdate.setImpresso(false);
        pedidoUpdate.setImpressoEm(null);

        pedidoUpdate.setObservacao(request.getObservacao());
        pedidoUpdate.setDataPedido(request.getDataPedido());
        pedidoUpdate.setDataEntrega(request.getDataEntrega());
        pedidoUpdate.setTipo(request.getTipo());
        pedidoUpdate.setCliente(clienteService.findByIdAndAtivo(request.getClienteId()));
        pedidoUpdate.setMostruario(resolverMostruario(request.getMostruarioId()));

        List<PedidoProduto> produtosAtuais = new ArrayList<>(pedidoUpdate.getProdutos());

        for (PedidoProdutoRequestDTO produtoRequest : request.getProdutosRequest()){
            PedidoProduto produtoExistente = null;

            if (produtoRequest.getId() != null) {
                for (PedidoProduto produto : pedidoUpdate.getProdutos()){
                    if (produtoRequest.getId().equals(produto.getId())) {
                        produtoExistente = produto;
                        break;
                    }
                }
            }

            for (PedidoProduto produto : produtoExistente != null ? List.<PedidoProduto>of() : pedidoUpdate.getProdutos()){
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
                if (produtoExistente.getProduto() == null && produtoExistente.getJoia() == null) {
                    // item avulso: mantém (e atualiza) descrição e grupo em vez de zerar
                    produtoExistente.setDescricao(produtoRequest.getDescricao());
                    produtoExistente.setGrupo(produtoRequest.getGrupoId() != null
                            ? grupoService.findById(produtoRequest.getGrupoId())
                            : null);
                } else {
                    produtoExistente.setDescricao(null);
                }

                if (!Objects.equals(produtoExistente.getVariacao(), produtoRequest.getVariacao())) {
                    produtoExistente.setVariacao(produtoRequest.getVariacao());
                }

                if (!Objects.equals(produtoExistente.getQuantidade(), produtoRequest.getQuantidade())) {
                    int jaCancelada = produtoExistente.getQuantidadeCancelada() != null ? produtoExistente.getQuantidadeCancelada() : 0;
                    if (produtoRequest.getQuantidade() < jaCancelada) {
                        throw new ItemInvalidoException("A quantidade não pode ser menor que a já cancelada (" + jaCancelada + ") do item");
                    }
                    produtoExistente.setQuantidade(produtoRequest.getQuantidade());
                }

                if (produtoExistente.getPreco() == null || produtoExistente.getPreco().compareTo(produtoRequest.getPreco()) != 0) {
                    produtoExistente.setPreco(produtoRequest.getPreco());
                }

                produtosAtuais.remove(produtoExistente);
            } else {
                PedidoProduto novoProduto = new PedidoProduto();

                preencherItem(novoProduto, produtoRequest, request.getTipo());

                novoProduto.setVariacao(produtoRequest.getVariacao());
                novoProduto.setQuantidade(produtoRequest.getQuantidade());
                novoProduto.setPreco(produtoRequest.getPreco());
                pedidoUpdate.adicionarProduto(novoProduto);
            }
        }

        for (PedidoProduto removerProduto : produtosAtuais) {
            pedidoUpdate.removerProduto(removerProduto);
        }
        return pedidoUpdate;
    }

    // cada pedido roda na própria transação: um que falha na validação não derruba os outros do lote
    public List<PedidoStatusLoteResultadoDTO> updateStatusLote(PedidoStatusLoteRequestDTO request){
        List<PedidoStatusLoteResultadoDTO> resultados = new ArrayList<>();
        for (Long id : new java.util.LinkedHashSet<>(request.getIds())) {
            try {
                PedidoStatusRequestDTO unitario = new PedidoStatusRequestDTO();
                unitario.setId(id);
                unitario.setStatus(request.getStatus());
                transactionTemplate.executeWithoutResult(tx -> {
                    Pedido pedido = updateStatus(unitario);
                    if (request.isImprimir()) {
                        pedido.setImpresso(true);
                        pedido.setImpressoEm(java.time.LocalDateTime.now());
                    }
                });
                resultados.add(new PedidoStatusLoteResultadoDTO(id, true, null));
            } catch (RuntimeException e) {
                resultados.add(new PedidoStatusLoteResultadoDTO(id, false, e.getMessage()));
            }
        }
        return resultados;
    }

    @Transactional
    public Pedido updateStatus(PedidoStatusRequestDTO statusRequest){
        // lock pessimista: dois cliques/usuários mudando o status do mesmo pedido não furam a regra de transição
        Pedido pedido = repository.findByIdForUpdate(statusRequest.getId())
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Pedido não encontrada. id: " + statusRequest.getId()));
        validation.validarTransicao(pedido.getId(), pedido.getStatus(), statusRequest.getStatus());
        if (statusRequest.getStatus() == StatusPedido.SEPARADO) {
            validation.validarTodosSeparados(pedido);
        }
        pedido.setStatus(statusRequest.getStatus());
        return pedido;
    }

    // marca/desmarca um item como já separado (lista de separação) - não mexe na impressão
    @Transactional
    public Pedido marcarItemSeparado(Long pedidoId, Long itemId, boolean separado){
        Pedido pedido = repository.findByIdForUpdate(pedidoId)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Pedido não encontrada. id: " + pedidoId));
        validation.validarMarcacaoSeparado(pedido);

        PedidoProduto item = pedido.getProdutos().stream()
                .filter(produto -> produto.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Item não encontrado no pedido. id: " + itemId));
        item.setSeparado(separado);
        return pedido;
    }

    private static final int MOTIVO_CANCELAMENTO_TAMANHO_MINIMO = 5;

    // cancelamento do pedido inteiro - igual ao padrão de nota fiscal: exige um motivo com no mínimo 5
    // caracteres e respeita a mesma máquina de transição de status (não dá pra cancelar um pedido ENVIADO)
    @Transactional
    public Pedido cancelar(Long id, String motivo){
        if (motivo == null || motivo.trim().length() < MOTIVO_CANCELAMENTO_TAMANHO_MINIMO) {
            throw new ItemInvalidoException("O motivo do cancelamento deve ter ao menos " + MOTIVO_CANCELAMENTO_TAMANHO_MINIMO + " caracteres");
        }
        Pedido pedido = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Pedido não encontrada. id: " + id));
        validation.validarTransicao(pedido.getId(), pedido.getStatus(), StatusPedido.CANCELADO);
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setMotivoCancelamento(motivo.trim());
        return pedido;
    }

    // cancelamento parcial/total de um item: não remove o item, só marca a quantidade cancelada (soma com
    // o que já tinha sido cancelado antes) - o item continua aparecendo no pedido e nos relatórios, com a
    // quantidade cancelada visível
    @Transactional
    public Pedido cancelarItem(Long pedidoId, Long itemId, Integer quantidade, String motivo){
        if (motivo == null || motivo.trim().length() < MOTIVO_CANCELAMENTO_TAMANHO_MINIMO) {
            throw new ItemInvalidoException("O motivo do cancelamento deve ter ao menos " + MOTIVO_CANCELAMENTO_TAMANHO_MINIMO + " caracteres");
        }

        Pedido pedido = this.findById(pedidoId);
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new ItemInvalidoException("Pedido " + pedidoId + " já está cancelado");
        }

        PedidoProduto item = pedido.getProdutos().stream()
                .filter(produto -> produto.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Item não encontrado no pedido. id: " + itemId));

        int jaCancelada = item.getQuantidadeCancelada() != null ? item.getQuantidadeCancelada() : 0;
        int disponivel = item.getQuantidade() - jaCancelada;
        if (quantidade == null || quantidade <= 0 || quantidade > disponivel) {
            throw new ItemInvalidoException("Quantidade a cancelar deve ser maior que 0 e no máximo " + disponivel);
        }

        item.setQuantidadeCancelada(jaCancelada + quantidade);
        item.setMotivoCancelamento(motivo.trim());

        // o que foi impresso antes não reflete mais o cancelamento
        pedido.setImpresso(false);
        pedido.setImpressoEm(null);

        // sem nenhum item disponível o pedido não faz mais sentido: cancela o pedido junto
        boolean todosCancelados = pedido.getProdutos().stream().allMatch(produto -> {
            int cancelada = produto.getQuantidadeCancelada() != null ? produto.getQuantidadeCancelada() : 0;
            return produto.getQuantidade() - cancelada <= 0;
        });
        if (todosCancelados && pedido.getStatus() != StatusPedido.ENVIADO) {
            pedido.setStatus(StatusPedido.CANCELADO);
            pedido.setMotivoCancelamento("Todos os itens foram cancelados. Último motivo: " + motivo.trim());
        }
        return pedido;
    }
}
