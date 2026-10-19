package com.manager.app.montagem;

import com.manager.app.cliente.ClienteService;
import com.manager.app.consignacao.Consignacao;
import com.manager.app.consignacao.ConsignacaoService;
import com.manager.app.consignacao.dto.ConsignacaoRequestDTO;
import com.manager.app.pedido.dto.PedidoRequestDTO;
import com.manager.app.shared.exception.ItemInvalidoException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import com.manager.app.pedido.Pedido;
import com.manager.app.pedido.PedidoService;
import com.manager.app.montagem.dto.MontagemRequestDTO;
import com.manager.app.mostruario.Mostruario;
import com.manager.app.mostruario.MostruarioService;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.ModoBusca;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.specification.SpecificationUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    public Page<MontagemMostruario> findAll(String busca, ModoBusca modoBusca, StatusMontagem status, UUID clienteId,
                                            LocalDate dataEntregaDe, LocalDate dataEntregaAte, Pageable pageable){
        Specification<MontagemMostruario> spec = null;
        if (busca != null && !busca.isBlank()) {
            spec = SpecificationUtils.and(spec, (root, query, cb) ->
                    SpecificationUtils.likeIgnoreCase(cb, root.get("cliente").get("nome"), busca, modoBusca));
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

    public MontagemMostruario findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Montagem não encontrada. id: " + id));
    }

    private Mostruario resolverMostruario(Long mostruarioId){
        return mostruarioId == null ? null : mostruarioService.findById(mostruarioId);
    }

    @Transactional
    public MontagemMostruario create(MontagemRequestDTO request){
        MontagemMostruario montagem = new MontagemMostruario();
        montagem.setCliente(clienteService.findByIdAndAtivo(request.getClienteId()));
        montagem.setMostruario(resolverMostruario(request.getMostruarioId()));
        montagem.setInstrucoes(request.getInstrucoes());
        montagem.setDataEntrega(request.getDataEntrega());
        montagem.setStatus(StatusMontagem.ABERTA);
        return repository.save(montagem);
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
        montagem.setInstrucoes(request.getInstrucoes());
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
    // tudo na mesma transação. O clienteId do body é substituído pelo da montagem; o mostruário da montagem
    // vale quando o body não traz um
    @Transactional
    public Pedido criarPedido(Long montagemId, PedidoRequestDTO request){
        MontagemMostruario montagem = this.findById(montagemId);
        validation.validarEditavel(montagem);

        request.setId(null);
        request.setClienteId(montagem.getCliente().getId());
        if (request.getMostruarioId() == null && montagem.getMostruario() != null) {
            request.setMostruarioId(montagem.getMostruario().getId());
        }
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
        if (request.getMostruarioId() == null && montagem.getMostruario() != null) {
            request.setMostruarioId(montagem.getMostruario().getId());
        }
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
        montagem.setStatus(StatusMontagem.CONCLUIDA);
        montagem.setConcluidaEm(LocalDateTime.now());
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
