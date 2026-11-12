package com.manager.app.planejadorrota;

import com.manager.app.cliente.Cliente;
import com.manager.app.cliente.ClienteRepository;
import com.manager.app.consignacao.Consignacao;
import com.manager.app.consignacao.ConsignacaoRepository;
import com.manager.app.endereco.Endereco;
import com.manager.app.endereco.EnderecoRepository;
import com.manager.app.planejadorrota.dto.PlanejadorRotaClienteRequestDTO;
import com.manager.app.planejadorrota.dto.PlanejadorRotaConsignacaoItemRequestDTO;
import com.manager.app.planejadorrota.dto.PlanejadorRotaRequestDTO;
import com.manager.app.planejadorrota.dto.PlanejadorRotaResumoDTO;
import com.manager.app.shared.ModoBusca;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.specification.SpecificationUtils;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.usuario.UsuarioResumoMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanejadorRotaService {

    private final PlanejadorRotaRepository repository;
    private final ClienteRepository clienteRepository;
    private final EnderecoRepository enderecoRepository;
    private final ConsignacaoRepository consignacaoRepository;
    private final PlanejadorRotaValidation validation;
    private final UsuarioResumoMapper usuarioResumoMapper;
    private final UsuarioAuditorAware usuarioAuditorAware;

    public Page<PlanejadorRota> findAll(String busca, ModoBusca modoBusca, LocalDate dataInicio, LocalDate dataFim, Pageable pageable){
        Specification<PlanejadorRota> spec = comBusca(busca, modoBusca);

        if (dataInicio != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> SpecificationUtils.apartirDe(cb, root.get("dataRota"), dataInicio));
        }
        if (dataFim != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> SpecificationUtils.ate(cb, root.get("dataRota"), dataFim));
        }

        return repository.findAll(spec, pageable);
    }

    public Page<PlanejadorRotaResumoDTO> findAllResumo(String busca, ModoBusca modoBusca, LocalDate dataInicio, LocalDate dataFim, Pageable pageable){
        return findAll(busca, modoBusca, dataInicio, dataFim, pageable).map(this::toResumoDTO);
    }

    private PlanejadorRotaResumoDTO toResumoDTO(PlanejadorRota rota){
        PlanejadorRotaResumoDTO dto = new PlanejadorRotaResumoDTO();
        dto.setId(rota.getId());
        dto.setNome(rota.getNome());
        dto.setDataRota(rota.getDataRota());
        dto.setQuantidadeClientes(rota.getItens().size());
        dto.setCreatedAt(rota.getCreatedAt());
        dto.setUpdatedAt(rota.getUpdatedAt());
        dto.setCreatedBy(usuarioResumoMapper.toResumoDTO(rota.getCreatedBy()));
        dto.setUpdatedBy(usuarioResumoMapper.toResumoDTO(rota.getUpdatedBy()));
        return dto;
    }

    private Specification<PlanejadorRota> comBusca(String busca, ModoBusca modoBusca){
        if (busca == null || busca.isBlank()) return null;
        return (root, query, cb) -> SpecificationUtils.likeIgnoreCase(cb, root.get("nome"), busca, modoBusca);
    }

    public PlanejadorRota findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Rota do planejador não encontrada. Id: " + id));
    }

    private Cliente resolverCliente(UUID clienteId){
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. Id: " + clienteId));
    }

    private Endereco resolverEndereco(Long enderecoId){
        return enderecoRepository.findById(enderecoId)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Endereço não encontrado. Id: " + enderecoId));
    }

    private Consignacao resolverConsignacao(Long consignacaoId){
        if (consignacaoId == null) return null;
        return consignacaoRepository.findById(consignacaoId)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Consignação não encontrada. Id: " + consignacaoId));
    }

    private void validarClienteUnico(List<PlanejadorRotaClienteRequestDTO> itens){
        long clientesDistintos = itens.stream().map(PlanejadorRotaClienteRequestDTO::getClienteId).distinct().count();
        if (clientesDistintos != itens.size()) {
            throw new ObjetoExistenteException("O mesmo cliente não pode constar mais de uma vez na rota do planejador.");
        }
    }

    // resolve e valida (pertence ao cliente, sem duplicata) a lista de consignacaoId dos requests -
    // usado tanto pra deixar quanto pra trazer, cada lado resolve e valida sozinho depois
    private List<Consignacao> resolverEValidarConsignacoes(Cliente cliente, List<PlanejadorRotaConsignacaoItemRequestDTO> requests){
        if (requests == null) return new ArrayList<>();
        List<Consignacao> consignacoes = new ArrayList<>();
        for (PlanejadorRotaConsignacaoItemRequestDTO request : requests) {
            Consignacao consignacao = consignacaoRepository.findById(request.getConsignacaoId())
                    .orElseThrow(() -> new ObjetoNaoEncontradoException("Consignação não encontrada. Id: " + request.getConsignacaoId()));
            consignacoes.add(consignacao);
        }
        validation.validarConsignacoesDoCliente(cliente, consignacoes);
        return consignacoes;
    }

    private void atualizarConsignacoesDeixar(PlanejadorRotaCliente item, List<PlanejadorRotaConsignacaoItemRequestDTO> requests){
        List<PlanejadorRotaConsignacaoDeixar> atuais = new ArrayList<>(item.getConsignacoesDeixar());
        List<PlanejadorRotaConsignacaoItemRequestDTO> pendentes = requests != null ? requests : new ArrayList<>();

        for (PlanejadorRotaConsignacaoItemRequestDTO request : pendentes) {
            PlanejadorRotaConsignacaoDeixar existente = atuais.stream()
                    .filter(c -> c.getConsignacao().getId().equals(request.getConsignacaoId()))
                    .findFirst().orElse(null);

            if (existente != null) {
                existente.setConfirmada(Boolean.TRUE.equals(request.getConfirmada()));
                atuais.remove(existente);
            } else {
                PlanejadorRotaConsignacaoDeixar novo = new PlanejadorRotaConsignacaoDeixar();
                novo.setConsignacao(resolverConsignacao(request.getConsignacaoId()));
                novo.setConfirmada(Boolean.TRUE.equals(request.getConfirmada()));
                item.adicionarConsignacaoDeixar(novo);
            }
        }

        for (PlanejadorRotaConsignacaoDeixar remover : atuais) {
            item.removerConsignacaoDeixar(remover);
        }
    }

    private void atualizarConsignacoesTrazer(PlanejadorRotaCliente item, List<PlanejadorRotaConsignacaoItemRequestDTO> requests){
        List<PlanejadorRotaConsignacaoTrazer> atuais = new ArrayList<>(item.getConsignacoesTrazer());
        List<PlanejadorRotaConsignacaoItemRequestDTO> pendentes = requests != null ? requests : new ArrayList<>();

        for (PlanejadorRotaConsignacaoItemRequestDTO request : pendentes) {
            PlanejadorRotaConsignacaoTrazer existente = atuais.stream()
                    .filter(c -> c.getConsignacao().getId().equals(request.getConsignacaoId()))
                    .findFirst().orElse(null);

            if (existente != null) {
                existente.setConfirmada(Boolean.TRUE.equals(request.getConfirmada()));
                atuais.remove(existente);
            } else {
                PlanejadorRotaConsignacaoTrazer novo = new PlanejadorRotaConsignacaoTrazer();
                novo.setConsignacao(resolverConsignacao(request.getConsignacaoId()));
                novo.setConfirmada(Boolean.TRUE.equals(request.getConfirmada()));
                item.adicionarConsignacaoTrazer(novo);
            }
        }

        for (PlanejadorRotaConsignacaoTrazer remover : atuais) {
            item.removerConsignacaoTrazer(remover);
        }
    }

    private void preencherItem(PlanejadorRotaCliente item, PlanejadorRotaClienteRequestDTO itemRequest){
        Cliente cliente = resolverCliente(itemRequest.getClienteId());
        Endereco endereco = resolverEndereco(itemRequest.getEnderecoId());
        validation.validarEndereco(cliente, endereco);

        // deixar/trazer sao opcionais - um cliente pode entrar na rota so pra
        // deixar consignacao nova sem trazer nenhuma de volta, ou vice-versa,
        // ou ate sem nenhuma das duas (ex.: so uma visita de cobranca)
        resolverEValidarConsignacoes(cliente, itemRequest.getConsignacoesDeixar());
        resolverEValidarConsignacoes(cliente, itemRequest.getConsignacoesTrazer());

        item.setCliente(cliente);
        item.setEndereco(endereco);
        item.setOrdem(itemRequest.getOrdem());
        validation.validarObservacao(itemRequest.getObservacao());
        item.setObservacao(itemRequest.getObservacao());
        item.setValorAPagar(itemRequest.getValorAPagar());
        atualizarConsignacoesDeixar(item, itemRequest.getConsignacoesDeixar());
        atualizarConsignacoesTrazer(item, itemRequest.getConsignacoesTrazer());
    }

    @Transactional
    public PlanejadorRota create(PlanejadorRotaRequestDTO request){
        validarClienteUnico(request.getItens());

        PlanejadorRota rota = new PlanejadorRota();
        validation.validarNome(request.getNome());
        rota.setNome(request.getNome());
        rota.setDataRota(request.getDataRota());

        int ordemAutomatica = 1;
        for (PlanejadorRotaClienteRequestDTO itemRequest : request.getItens()) {
            PlanejadorRotaCliente item = new PlanejadorRotaCliente();
            if (itemRequest.getOrdem() == null) {
                itemRequest.setOrdem(ordemAutomatica);
            }
            preencherItem(item, itemRequest);
            rota.adicionarItem(item);
            ordemAutomatica++;
        }

        return repository.save(rota);
    }

    @Transactional
    public PlanejadorRota update(PlanejadorRotaRequestDTO request){
        PlanejadorRota rotaUpdate = this.findById(request.getId());
        validarClienteUnico(request.getItens());

        validation.validarNome(request.getNome());
        rotaUpdate.setNome(request.getNome());
        rotaUpdate.setDataRota(request.getDataRota());

        List<PlanejadorRotaCliente> itensAtuais = new ArrayList<>(rotaUpdate.getItens());

        int ordemAutomatica = 1;
        for (PlanejadorRotaClienteRequestDTO itemRequest : request.getItens()) {
            if (itemRequest.getOrdem() == null) {
                itemRequest.setOrdem(ordemAutomatica);
            }

            PlanejadorRotaCliente itemExistente = itemRequest.getId() != null
                    ? itensAtuais.stream().filter(i -> i.getId().equals(itemRequest.getId())).findFirst().orElse(null)
                    : null;

            if (itemExistente != null) {
                preencherItem(itemExistente, itemRequest);
                itensAtuais.remove(itemExistente);
            } else {
                PlanejadorRotaCliente novoItem = new PlanejadorRotaCliente();
                preencherItem(novoItem, itemRequest);
                rotaUpdate.adicionarItem(novoItem);
            }
            ordemAutomatica++;
        }

        for (PlanejadorRotaCliente itemRemover : itensAtuais) {
            rotaUpdate.removerItem(itemRemover);
        }

        LocalDateTime agora = LocalDateTime.now();
        var usuarioAtual = usuarioAuditorAware.getCurrentAuditor().orElse(null);
        repository.tocarAtualizacao(rotaUpdate.getId(), agora, usuarioAtual);
        rotaUpdate.setUpdatedAt(agora);
        rotaUpdate.setUpdatedBy(usuarioAtual);

        return rotaUpdate;
    }

    @Transactional
    public void delete(Long id){
        PlanejadorRota rota = this.findById(id);
        repository.delete(rota);
    }
}
