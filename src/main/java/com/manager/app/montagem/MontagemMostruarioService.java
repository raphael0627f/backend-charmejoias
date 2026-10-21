package com.manager.app.montagem;

import com.manager.app.cliente.ClienteService;
import com.manager.app.consignacao.Consignacao;
import com.manager.app.consignacao.ConsignacaoService;
import com.manager.app.consignacao.ConsignacaoItemAdicionar;
import com.manager.app.consignacao.ConsignacaoRepository;
import com.manager.app.pedido.PedidoRepository;
import com.manager.app.pedido.StatusPedido;
import com.manager.app.consignacao.dto.ConsignacaoRequestDTO;
import com.manager.app.montagem.dto.MontagemConferenciaDTO;
import com.manager.app.montagem.dto.MontagemInstrucaoRequestDTO;
import com.manager.app.pedido.dto.PedidoRequestDTO;
import com.manager.app.pedido.dto.PedidoStatusRequestDTO;
import com.manager.app.shared.exception.ItemInvalidoException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import com.manager.app.pedido.Pedido;
import com.manager.app.pedido.PedidoService;
import com.manager.app.montagem.dto.MontagemRequestDTO;
import com.manager.app.montagem.dto.MontagemResumoDTO;
import com.manager.app.mostruario.Mostruario;
import com.manager.app.mostruario.MostruarioService;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.ModoBusca;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.specification.SpecificationUtils;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MontagemMostruarioService {

    private final MontagemMostruarioRepository repository;
    private final MontagemValidation validation;
    private final ClienteService clienteService;
    private final MostruarioService mostruarioService;
    private final UsuarioAuditorAware auditorAware;
    private final ConsignacaoService consignacaoService;
    private final PedidoService pedidoService;
    private final Validator validator;
    private final MontagemConferencia conferencia;
    private final ConsignacaoRepository consignacaoRepository;
    private final PedidoRepository pedidoRepository;
    private final MontagemItemIncluidoRepository itemIncluidoRepository;

    // preenche, para a página inteira da listagem, as quantidades de consignações/pedidos e o andamento
    // (uma consulta de agregação por item, nunca carregando as listas de cada montagem)
    public void preencherResumos(List<MontagemResumoDTO> resumos){
        List<Long> ids = resumos.stream().map(MontagemResumoDTO::getId).toList();
        if (ids.isEmpty()) return;

        Map<Long, Integer> consignacoes = contarConsignacoes(ids);
        Map<Long, MontagemProgressoProjecao> pedidos = indexar(repository.progressoPedidos(ids));
        Map<Long, MontagemProgressoProjecao> instrucoes = indexar(repository.progressoInstrucoes(ids));
        Map<Long, Integer> necessarios = repository.necessarioDosPedidos(ids).stream()
                .collect(Collectors.toMap(MontagemContagemProjecao::getMontagemId, c -> c.getTotal().intValue()));
        Map<Long, Integer> incluidos = itemIncluidoRepository.somarIncluidos(ids).stream()
                .collect(Collectors.toMap(MontagemContagemProjecao::getMontagemId, c -> c.getTotal().intValue()));

        for (MontagemResumoDTO dto : resumos) {
            Long id = dto.getId();
            MontagemProgressoProjecao ped = pedidos.get(id);
            MontagemProgressoProjecao ins = instrucoes.get(id);
            dto.setQuantidadeConsignacoes(consignacoes.getOrDefault(id, 0));
            dto.setQuantidadePedidos(ped != null ? ped.getTotal().intValue() : 0);
            dto.setPedidosSeparados(ped != null ? ped.getConcluidos().intValue() : 0);
            dto.setInstrucoesTotal(ins != null ? ins.getTotal().intValue() : 0);
            dto.setInstrucoesConcluidas(ins != null ? ins.getConcluidos().intValue() : 0);
            int necessario = necessarios.getOrDefault(id, 0);
            dto.setProdutosNecessarios(necessario);
            dto.setProdutosIncluidos(Math.min(incluidos.getOrDefault(id, 0), necessario));
            dto.setAndamento(calcularAndamento(dto));
        }
    }

    private static Map<Long, MontagemProgressoProjecao> indexar(List<MontagemProgressoProjecao> linhas){
        return linhas.stream().collect(Collectors.toMap(MontagemProgressoProjecao::getMontagemId, l -> l));
    }

    // média das partes que existem (instruções feitas, pedidos separados, produtos incluídos); concluída = 100,
    // cancelada = sem andamento
    private static Integer calcularAndamento(MontagemResumoDTO dto){
        if (dto.getStatus() == StatusMontagem.CANCELADA) return null;
        if (dto.getStatus() == StatusMontagem.CONCLUIDA) return 100;
        List<Double> partes = new ArrayList<>();
        if (dto.getInstrucoesTotal() > 0) partes.add((double) dto.getInstrucoesConcluidas() / dto.getInstrucoesTotal());
        if (dto.getQuantidadePedidos() > 0) partes.add((double) dto.getPedidosSeparados() / dto.getQuantidadePedidos());
        if (dto.getProdutosNecessarios() > 0) partes.add((double) dto.getProdutosIncluidos() / dto.getProdutosNecessarios());
        if (partes.isEmpty()) return 0;
        return (int) Math.round(partes.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100);
    }

    // quantidades de consignações/pedidos por montagem para a listagem (uma consulta cada, para a página inteira)
    public Map<Long, Integer> contarConsignacoes(List<Long> ids){
        return ids.isEmpty() ? Map.of() : repository.contarConsignacoes(ids).stream()
                .collect(Collectors.toMap(MontagemContagemProjecao::getMontagemId, c -> c.getTotal().intValue()));
    }

    public Map<Long, Integer> contarPedidos(List<Long> ids){
        return ids.isEmpty() ? Map.of() : repository.contarPedidos(ids).stream()
                .collect(Collectors.toMap(MontagemContagemProjecao::getMontagemId, c -> c.getTotal().intValue()));
    }

    public Page<MontagemMostruario> findAll(String busca, ModoBusca modoBusca, StatusMontagem status, UUID clienteId,
                                            LocalDate dataEntregaDe, LocalDate dataEntregaAte, Pageable pageable){
        Specification<MontagemMostruario> spec = null;
        if (busca != null && !busca.isBlank()) {
            spec = SpecificationUtils.and(spec, comBusca(busca, modoBusca));
        }
        if (status != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (clienteId != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> cb.equal(root.get("cliente").get("id"), clienteId));
        }
        if (dataEntregaDe != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> SpecificationUtils.apartirDe(cb, root.get("dataEntrega"), dataEntregaDe));
        }
        if (dataEntregaAte != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> SpecificationUtils.ate(cb, root.get("dataEntrega"), dataEntregaAte));
        }
        return repository.findAll(spec, pageable);
    }

    // mesma busca da listagem de consignações: número da montagem, código/nome do cliente e o kit (descrição do
    // mostruário, ex.: "5245" - o que o usuário vê na tela, não o id interno)
    private Specification<MontagemMostruario> comBusca(String busca, ModoBusca modoBusca){
        Long comoNumero = SpecificationUtils.comoLong(busca);
        return (root, query, cb) -> {
            var cliente = root.join("cliente", JoinType.LEFT);
            var mostruario = root.join("mostruario", JoinType.LEFT);
            List<Predicate> predicates = new ArrayList<>(List.of(
                    SpecificationUtils.likeIgnoreCase(cb, cliente.get("codigo"), busca, modoBusca),
                    SpecificationUtils.likeIgnoreCase(cb, cliente.get("nome"), busca, modoBusca),
                    SpecificationUtils.likeIgnoreCase(cb, mostruario.get("descricao"), busca, modoBusca)
            ));
            if (comoNumero != null) {
                predicates.add(cb.equal(root.get("id"), comoNumero));
            }
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    public MontagemMostruario findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Montagem não encontrada. id: " + id));
    }

    private Mostruario resolverMostruario(Long mostruarioId){
        return mostruarioId == null ? null : mostruarioService.findById(mostruarioId);
    }

    // mantém as instruções enviadas (com id = existentes, preservando se já foram feitas), cria as novas e remove as que
    // não vieram; a ordem é a da lista enviada
    private void sincronizarInstrucoes(MontagemMostruario montagem, List<MontagemInstrucaoRequestDTO> enviadas){
        Map<Long, MontagemInstrucao> atuais = montagem.getInstrucoes().stream()
                .collect(Collectors.toMap(MontagemInstrucao::getId, i -> i));
        List<MontagemInstrucao> resultado = new ArrayList<>();
        int ordem = 0;
        for (MontagemInstrucaoRequestDTO dto : enviadas != null ? enviadas : List.<MontagemInstrucaoRequestDTO>of()) {
            if (dto.getTexto() == null || dto.getTexto().isBlank()) continue;
            MontagemInstrucao instrucao = null;
            if (dto.getId() != null) {
                instrucao = atuais.get(dto.getId());
                if (instrucao == null) {
                    throw new ObjetoNaoEncontradoException("Instrução " + dto.getId() + " não pertence à montagem " + montagem.getId());
                }
            }
            if (instrucao == null) {
                instrucao = new MontagemInstrucao();
                instrucao.setMontagem(montagem);
            }
            instrucao.setTexto(dto.getTexto().trim());
            instrucao.setOrdem(ordem++);
            resultado.add(instrucao);
        }
        montagem.getInstrucoes().clear();
        montagem.getInstrucoes().addAll(resultado);
    }

    // ao criar a montagem, vincula sozinho (sem perguntar) as consignações e os pedidos do mesmo cliente e da mesma data
    // de entrega: consignação só se ainda não confirmada, pedido só se não cancelado, e nunca o que já está em outra montagem
    private void vincularExistentesDoCliente(MontagemMostruario montagem){
        UUID clienteId = montagem.getCliente().getId();
        LocalDate dataEntrega = montagem.getDataEntrega();

        for (Consignacao consignacao : consignacaoRepository.findByCliente_IdAndDataEntregaAndConfirmadaFalse(clienteId, dataEntrega)) {
            if (repository.findByConsignacoes_Id(consignacao.getId()).isEmpty()) {
                montagem.getConsignacoes().add(consignacao);
            }
        }
        for (Pedido pedido : pedidoRepository.findByCliente_IdAndDataEntregaAndStatusNot(clienteId, dataEntrega, StatusPedido.CANCELADO)) {
            if (repository.findByPedidos_Id(pedido.getId()).isEmpty()) {
                montagem.getPedidos().add(pedido);
            }
        }
    }

    // marcar/desmarcar uma instrução como feita: não depende de salvar o formulário da montagem
    @Transactional
    public MontagemMostruario marcarInstrucao(Long montagemId, Long instrucaoId, boolean concluida){
        MontagemMostruario montagem = this.findById(montagemId);
        validation.validarEditavel(montagem);
        MontagemInstrucao instrucao = montagem.getInstrucoes().stream()
                .filter(i -> i.getId().equals(instrucaoId))
                .findFirst()
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Instrução " + instrucaoId + " não pertence à montagem " + montagemId));
        instrucao.setConcluida(concluida);
        instrucao.setConcluidaEm(concluida ? LocalDateTime.now() : null);
        instrucao.setConcluidaPor(concluida ? auditorAware.getCurrentAuditor().orElse(null) : null);
        return montagem;
    }

    @Transactional
    public MontagemMostruario create(MontagemRequestDTO request){
        MontagemMostruario montagem = new MontagemMostruario();
        montagem.setCliente(clienteService.findByIdAndAtivo(request.getClienteId()));
        montagem.setMostruario(resolverMostruario(request.getMostruarioId()));
        sincronizarInstrucoes(montagem, request.getInstrucoes());
        montagem.setDataEntrega(request.getDataEntrega());
        montagem.setStatus(StatusMontagem.ABERTA);
        MontagemMostruario salva = repository.save(montagem);
        vincularExistentesDoCliente(salva);
        return salva;
    }

    @Transactional
    public MontagemMostruario update(MontagemRequestDTO request){
        MontagemMostruario montagem = this.findById(request.getId());
        validation.validarEditavel(montagem);
        validation.validarTrocaCliente(montagem, request.getClienteId());

        if (!montagem.getCliente().getId().equals(request.getClienteId())) {
            montagem.setCliente(clienteService.findByIdAndAtivo(request.getClienteId()));
        }
        montagem.setMostruario(resolverMostruario(request.getMostruarioId()));
        sincronizarInstrucoes(montagem, request.getInstrucoes());
        montagem.setDataEntrega(request.getDataEntrega());
        return montagem;
    }

    @Transactional
    public void delete(Long id){
        MontagemMostruario montagem = this.findById(id);
        validation.validarExclusao(montagem);
        repository.delete(montagem);
    }

    // desvincular só tira o item da lista da montagem; a consignação/pedido em si não é alterado
    @Transactional
    public MontagemMostruario vincularConsignacao(Long montagemId, Long consignacaoId){
        MontagemMostruario montagem = this.findById(montagemId);
        Consignacao consignacao = consignacaoService.findById(consignacaoId);
        validation.validarVinculoConsignacao(montagem, consignacao, repository.findByConsignacoes_Id(consignacaoId));
        montagem.getConsignacoes().add(consignacao);
        return montagem;
    }

    @Transactional
    public MontagemMostruario desvincularConsignacao(Long montagemId, Long consignacaoId){
        MontagemMostruario montagem = this.findById(montagemId);
        validation.validarEditavel(montagem);
        if (!montagem.getConsignacoes().removeIf(c -> c.getId().equals(consignacaoId))) {
            throw new ObjetoNaoEncontradoException("Consignação " + consignacaoId + " não está vinculada à montagem " + montagemId);
        }
        return montagem;
    }

    @Transactional
    public MontagemMostruario vincularPedido(Long montagemId, Long pedidoId){
        MontagemMostruario montagem = this.findById(montagemId);
        Pedido pedido = pedidoService.findById(pedidoId);
        validation.validarVinculoPedido(montagem, pedido, repository.findByPedidos_Id(pedidoId));
        montagem.getPedidos().add(pedido);
        return montagem;
    }

    @Transactional
    public MontagemMostruario desvincularPedido(Long montagemId, Long pedidoId){
        MontagemMostruario montagem = this.findById(montagemId);
        validation.validarEditavel(montagem);
        if (!montagem.getPedidos().removeIf(p -> p.getId().equals(pedidoId))) {
            throw new ObjetoNaoEncontradoException("Pedido " + pedidoId + " não está vinculado à montagem " + montagemId);
        }
        return montagem;
    }

    // cria o pedido pelo PedidoService (mesmas regras de itens/preços) já para o cliente da montagem e o vincula,
    // tudo na mesma transação. O clienteId do body é substituído pelo da montagem; o mostruário vem só do body
    @Transactional
    public Pedido criarPedido(Long montagemId, PedidoRequestDTO request){
        MontagemMostruario montagem = this.findById(montagemId);
        validation.validarEditavel(montagem);

        request.setId(null);
        request.setClienteId(montagem.getCliente().getId());
        validar(request);

        Pedido pedido = pedidoService.create(request);
        montagem.getPedidos().add(pedido);
        return pedido;
    }

    @Transactional
    public Consignacao criarConsignacao(Long montagemId, ConsignacaoRequestDTO request){
        MontagemMostruario montagem = this.findById(montagemId);
        validation.validarEditavel(montagem);

        request.setId(null);
        request.setClienteId(montagem.getCliente().getId());
        validar(request);

        Consignacao consignacao = consignacaoService.create(request);
        montagem.getConsignacoes().add(consignacao);
        return consignacao;
    }

    // o body chega sem @Valid no controller: o clienteId/mostruarioId podem ser preenchidos aqui antes de validar
    private void validar(Object request){
        Set<ConstraintViolation<Object>> violacoes = validator.validate(request);
        if (!violacoes.isEmpty()) {
            throw new ItemInvalidoException(violacoes.stream()
                    .map(v -> v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; ")));
        }
    }

    // conferência sempre recalculada (pedidos/consignações podem mudar a qualquer momento)
    @Transactional
    public MontagemConferenciaDTO conferir(Long id){
        return conferencia.calcular(this.findById(id));
    }

    // SOMA, na consignação escolhida, os produtos dos pedidos já separados que ainda não foram incluídos: consignação com
    // 3 do produto + pedido com 4 = 7 (o que a consignação já tinha não abate o do pedido). O que foi incluído fica
    // registrado por item de pedido, para a conferência saber o que já entrou. O preço é o de venda do produto/variação,
    // como no cadastro da consignação
    @Transactional
    public MontagemConferenciaDTO adicionarPendentes(Long montagemId, Long consignacaoId){
        MontagemMostruario montagem = this.findById(montagemId);
        validation.validarEditavel(montagem);
        montagem.getConsignacoes().stream()
                .filter(c -> c.getId().equals(consignacaoId))
                .findFirst()
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Consignação " + consignacaoId + " não está vinculada à montagem " + montagemId));

        // só entram produtos de pedidos já separados
        var pendentes = conferencia.itensPendentes(montagem).stream()
                .filter(p -> p.separado() && p.pendente() > 0)
                .toList();
        if (pendentes.isEmpty()) {
            throw new ItemInvalidoException("Não há produtos pendentes de pedidos separados para adicionar às consignações da montagem " + montagemId);
        }

        // uma linha por produto/variação, com a soma do pendente dos itens
        Map<String, ConsignacaoItemAdicionar> somados = new java.util.LinkedHashMap<>();
        for (var p : pendentes) {
            Long variacaoId = p.item().getProdutoVariacao() != null ? p.item().getProdutoVariacao().getId() : null;
            String chave = p.item().getProduto().getId() + ":" + (variacaoId != null ? variacaoId : "");
            somados.merge(chave,
                    new ConsignacaoItemAdicionar(p.item().getProduto().getId(), variacaoId, p.pendente()),
                    (a, b) -> new ConsignacaoItemAdicionar(a.produtoId(), a.produtoVariacaoId(), a.quantidade() + b.quantidade()));
        }
        consignacaoService.adicionarProdutos(consignacaoId, new ArrayList<>(somados.values()));

        LocalDateTime agora = LocalDateTime.now();
        for (var p : pendentes) {
            MontagemItemIncluido registro = new MontagemItemIncluido();
            registro.setMontagemId(montagemId);
            registro.setConsignacaoId(consignacaoId);
            registro.setPedidoProdutoId(p.item().getId());
            registro.setQuantidade(p.pendente());
            registro.setCreatedAt(agora);
            itemIncluidoRepository.save(registro);
        }
        return conferencia.calcular(montagem);
    }

    @Transactional
    public MontagemMostruario iniciar(Long id){
        MontagemMostruario montagem = this.findById(id);
        validation.validarIniciar(montagem);
        montagem.setStatus(StatusMontagem.EM_MONTAGEM);
        montagem.setIniciadaEm(LocalDateTime.now());
        montagem.setIniciadaPor(auditorAware.getCurrentAuditor().orElse(null));
        return montagem;
    }

    @Transactional
    public MontagemMostruario concluir(Long id){
        MontagemMostruario montagem = this.findById(id);
        validation.validarConcluir(montagem);
        validation.validarConferencia(conferencia.calcular(montagem));
        montagem.setStatus(StatusMontagem.CONCLUIDA);
        montagem.setConcluidaEm(LocalDateTime.now());
        // pedido sem cliente e/ou sem data de entrega recebe os da montagem (nenhum pedido pode ser enviado sem eles);
        // cancelados não são alterados
        for (Pedido pedido : montagem.getPedidos()) {
            if (pedido.getStatus() == StatusPedido.CANCELADO) continue;
            if (pedido.getCliente() == null) {
                pedido.setCliente(montagem.getCliente());
            }
            if (pedido.getDataEntrega() == null) {
                pedido.setDataEntrega(montagem.getDataEntrega());
            }
        }
        // os pedidos separados da montagem passam para Enviado (cancelados e já enviados ficam como estão);
        // na mesma transação: se alguma troca falhar, a montagem também não é concluída
        for (Pedido pedido : montagem.getPedidos()) {
            if (pedido.getStatus() == StatusPedido.SEPARADO) {
                PedidoStatusRequestDTO envio = new PedidoStatusRequestDTO();
                envio.setId(pedido.getId());
                envio.setStatus(StatusPedido.ENVIADO);
                pedidoService.updateStatus(envio);
            }
        }
        return montagem;
    }

    @Transactional
    public MontagemMostruario cancelar(Long id, String motivo){
        MontagemMostruario montagem = this.findById(id);
        validation.validarCancelar(montagem, motivo);
        montagem.setStatus(StatusMontagem.CANCELADA);
        montagem.setMotivoCancelamento(motivo.trim());
        return montagem;
    }
}
