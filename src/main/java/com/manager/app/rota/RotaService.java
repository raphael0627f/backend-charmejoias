package com.manager.app.rota;

import com.manager.app.cliente.Cliente;
import com.manager.app.cliente.ClienteRepository;
import com.manager.app.consignacao.Consignacao;
import com.manager.app.consignacao.ConsignacaoRepository;
import com.manager.app.endereco.Endereco;
import com.manager.app.endereco.EnderecoRepository;
import com.manager.app.rota.dto.RotaClienteRequestDTO;
import com.manager.app.rota.dto.RotaConsignacaoItemRequestDTO;
import com.manager.app.rota.dto.RotaRequestDTO;
import com.manager.app.rota.dto.RotaResumoDTO;
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
public class RotaService {

    private final RotaRepository repository;
    private final ClienteRepository clienteRepository;
    private final EnderecoRepository enderecoRepository;
    private final ConsignacaoRepository consignacaoRepository;
    private final RotaValidation validation;
    private final UsuarioResumoMapper usuarioResumoMapper;
    private final UsuarioAuditorAware usuarioAuditorAware;

    public Page<Rota> findAll(String busca, ModoBusca modoBusca, LocalDate dataInicio, LocalDate dataFim, Pageable pageable){
        Specification<Rota> spec = comBusca(busca, modoBusca);

        if (dataInicio != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> SpecificationUtils.apartirDe(cb, root.get("dataRota"), dataInicio));
        }
        if (dataFim != null) {
            spec = SpecificationUtils.and(spec, (root, query, cb) -> SpecificationUtils.ate(cb, root.get("dataRota"), dataFim));
        }

        return repository.findAll(spec, pageable);
    }

    public Page<RotaResumoDTO> findAllResumo(String busca, ModoBusca modoBusca, LocalDate dataInicio, LocalDate dataFim, Pageable pageable){
        return findAll(busca, modoBusca, dataInicio, dataFim, pageable).map(this::toResumoDTO);
    }

    private RotaResumoDTO toResumoDTO(Rota rota){
        RotaResumoDTO dto = new RotaResumoDTO();
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

    private Specification<Rota> comBusca(String busca, ModoBusca modoBusca){
        if (busca == null || busca.isBlank()) return null;
        return (root, query, cb) -> SpecificationUtils.likeIgnoreCase(cb, root.get("nome"), busca, modoBusca);
    }

    public Rota findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Rota não encontrada. id: " + id));
    }

    private Cliente resolverCliente(UUID clienteId){
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. id: " + clienteId));
    }

    private Endereco resolverEndereco(Long enderecoId){
        return enderecoRepository.findById(enderecoId)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Endereço não encontrado. id: " + enderecoId));
    }

    private Consignacao resolverConsignacao(Long consignacaoId){
        if (consignacaoId == null) return null;
        return consignacaoRepository.findById(consignacaoId)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Consignação não encontrada. id: " + consignacaoId));
    }

    private void validarClienteUnico(List<RotaClienteRequestDTO> itens){
        long clientesDistintos = itens.stream().map(RotaClienteRequestDTO::getClienteId).distinct().count();
        if (clientesDistintos != itens.size()) {
            throw new ObjetoExistenteException("Um mesmo Cliente não pode aparecer mais de uma vez na mesma Rota.");
        }
    }

    // resolve e valida (pertence ao cliente, sem duplicata) a lista de consignacaoId dos requests -
    // usado tanto pra deixar quanto pra trazer, cada lado resolve e valida sozinho depois
    private List<Consignacao> resolverEValidarConsignacoes(Cliente cliente, List<RotaConsignacaoItemRequestDTO> requests){
        if (requests == null) return new ArrayList<>();
        List<Consignacao> consignacoes = new ArrayList<>();
        for (RotaConsignacaoItemRequestDTO request : requests) {
            Consignacao consignacao = consignacaoRepository.findById(request.getConsignacaoId())
                    .orElseThrow(() -> new ObjetoNaoEncontradoException("Consignação não encontrada. id: " + request.getConsignacaoId()));
            consignacoes.add(consignacao);
        }
        validation.validarConsignacoesDoCliente(cliente, consignacoes);
        return consignacoes;
    }

    private void atualizarConsignacoesDeixar(RotaCliente item, List<RotaConsignacaoItemRequestDTO> requests){
        List<RotaConsignacaoDeixar> atuais = new ArrayList<>(item.getConsignacoesDeixar());
        List<RotaConsignacaoItemRequestDTO> pendentes = requests != null ? requests : new ArrayList<>();

        for (RotaConsignacaoItemRequestDTO request : pendentes) {
            RotaConsignacaoDeixar existente = atuais.stream()
                    .filter(c -> c.getConsignacao().getId().equals(request.getConsignacaoId()))
                    .findFirst().orElse(null);

            if (existente != null) {
                existente.setConfirmada(Boolean.TRUE.equals(request.getConfirmada()));
                atuais.remove(existente);
            } else {
                RotaConsignacaoDeixar novo = new RotaConsignacaoDeixar();
                novo.setConsignacao(resolverConsignacao(request.getConsignacaoId()));
                novo.setConfirmada(Boolean.TRUE.equals(request.getConfirmada()));
                item.adicionarConsignacaoDeixar(novo);
            }
        }

        for (RotaConsignacaoDeixar remover : atuais) {
            item.removerConsignacaoDeixar(remover);
        }
    }

    private void atualizarConsignacoesTrazer(RotaCliente item, List<RotaConsignacaoItemRequestDTO> requests){
        List<RotaConsignacaoTrazer> atuais = new ArrayList<>(item.getConsignacoesTrazer());
        List<RotaConsignacaoItemRequestDTO> pendentes = requests != null ? requests : new ArrayList<>();

        for (RotaConsignacaoItemRequestDTO request : pendentes) {
            RotaConsignacaoTrazer existente = atuais.stream()
                    .filter(c -> c.getConsignacao().getId().equals(request.getConsignacaoId()))
                    .findFirst().orElse(null);

            if (existente != null) {
                existente.setConfirmada(Boolean.TRUE.equals(request.getConfirmada()));
                atuais.remove(existente);
            } else {
                RotaConsignacaoTrazer novo = new RotaConsignacaoTrazer();
                novo.setConsignacao(resolverConsignacao(request.getConsignacaoId()));
                novo.setConfirmada(Boolean.TRUE.equals(request.getConfirmada()));
                item.adicionarConsignacaoTrazer(novo);
            }
        }

        for (RotaConsignacaoTrazer remover : atuais) {
            item.removerConsignacaoTrazer(remover);
        }
    }

    private void preencherItem(RotaCliente item, RotaClienteRequestDTO itemRequest){
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
        item.setObservacao(itemRequest.getObservacao());
        item.setValorAPagar(itemRequest.getValorAPagar());
        atualizarConsignacoesDeixar(item, itemRequest.getConsignacoesDeixar());
        atualizarConsignacoesTrazer(item, itemRequest.getConsignacoesTrazer());
    }

    @Transactional
    public Rota create(RotaRequestDTO request){
        validarClienteUnico(request.getItens());

        Rota rota = new Rota();
        rota.setNome(request.getNome());
        rota.setDataRota(request.getDataRota());

        int ordemAutomatica = 1;
        for (RotaClienteRequestDTO itemRequest : request.getItens()) {
            RotaCliente item = new RotaCliente();
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
    public Rota update(RotaRequestDTO request){
        Rota rotaUpdate = this.findById(request.getId());
        validarClienteUnico(request.getItens());

        rotaUpdate.setNome(request.getNome());
        rotaUpdate.setDataRota(request.getDataRota());

        List<RotaCliente> itensAtuais = new ArrayList<>(rotaUpdate.getItens());

        int ordemAutomatica = 1;
        for (RotaClienteRequestDTO itemRequest : request.getItens()) {
            if (itemRequest.getOrdem() == null) {
                itemRequest.setOrdem(ordemAutomatica);
            }

            RotaCliente itemExistente = itemRequest.getId() != null
                    ? itensAtuais.stream().filter(i -> i.getId().equals(itemRequest.getId())).findFirst().orElse(null)
                    : null;

            if (itemExistente != null) {
                preencherItem(itemExistente, itemRequest);
                itensAtuais.remove(itemExistente);
            } else {
                RotaCliente novoItem = new RotaCliente();
                preencherItem(novoItem, itemRequest);
                rotaUpdate.adicionarItem(novoItem);
            }
            ordemAutomatica++;
        }

        for (RotaCliente itemRemover : itensAtuais) {
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
        Rota rota = this.findById(id);
        repository.delete(rota);
    }
}
