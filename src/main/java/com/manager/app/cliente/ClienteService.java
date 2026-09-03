package com.manager.app.cliente;

import com.manager.app.endereco.Endereco;
import com.manager.app.grupo.Grupo;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.grupo.GrupoValidation;
import com.manager.app.shared.specification.SpecificationUtils;
import com.manager.app.grupo.GrupoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import com.manager.app.endereco.EnderecoService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository repository;
    private final GrupoRepository grupoRepository;
    private final EnderecoService enderecoService;
    private final ClienteValidation validation;
    private final GrupoValidation grupoValidation;

    public Page<Cliente> findAll(String busca, Boolean ativo, Long grupoId, Pageable pageable){
        Specification<Cliente> spec = Specification.where(comBusca(busca));

        if (ativo != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("ativo"), ativo));
        }

        if (grupoId != null) {
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                return cb.equal(root.join("grupos").get("id"), grupoId);
            });
        }

        return repository.findAll(spec, pageable);
    }

    private Specification<Cliente> comBusca(String busca){
        if (busca == null || busca.isBlank()) return (root, query, cb) -> cb.conjunction();
        return (root, query, cb) -> cb.or(
                SpecificationUtils.likeIgnoreCase(cb, root.get("nome"), busca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("codigo"), busca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("cpf"), busca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("documento"), busca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("telefone"), busca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("celular"), busca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("email"), busca)
        );
    }

    public Cliente findById(UUID id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. id: " + id));
    }

    public Cliente findByIdAndAtivo(UUID id){
        return repository.findByIdAndAtivoTrue(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado ou inativo. id: " + id));
    }

    public Cliente findByCodigo(String codigo){
        return repository.findByCodigo(codigo).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. codigo: " + codigo));
    }

    @Transactional
    public Cliente create(Cliente cliente, List<Long> gruposIds){
        validation.codigoExistenteCreate(cliente.getCodigo());

        if (cliente.getEndereco() != null) {
            cliente.setEndereco(enderecoService.create(cliente.getEndereco()));
        }

        if(!grupoValidation.gruposEmpty(gruposIds)){
            List<Grupo> grupos = grupoRepository.findAllById(gruposIds);
            if (grupos.size() != gruposIds.size()) {
                throw new ObjetoNaoEncontradoException("Um ou mais grupos não encontrados");
            }
            cliente.setGrupos(grupos);
        }
        return repository.save(cliente);
    }

    @Transactional
    public Cliente update(Cliente cliente, List<Long> gruposIds){
        validation.codigoExistenteUpdate(cliente.getCodigo(), cliente.getId());
        Cliente clienteUpdate = repository.findById(cliente.getId()).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. id: " + cliente.getId()));
        Endereco endereco = enderecoService.update(cliente.getEndereco());

        clienteUpdate.setCodigo(cliente.getCodigo());
        clienteUpdate.setReferencia(cliente.getReferencia());
        clienteUpdate.setNome(cliente.getNome());
        clienteUpdate.setFiliacao(cliente.getFiliacao());
        clienteUpdate.setCpf(cliente.getCpf());
        clienteUpdate.setDocumento(cliente.getDocumento());
        clienteUpdate.setGenero(cliente.getGenero());
        clienteUpdate.setTelefone(cliente.getTelefone());
        clienteUpdate.setCelular(cliente.getCelular());
        clienteUpdate.setEmail(cliente.getEmail());
        clienteUpdate.setObservacao(cliente.getObservacao());
        clienteUpdate.setDataNascimento(cliente.getDataNascimento());
        clienteUpdate.setAtivo(cliente.isAtivo());
        clienteUpdate.setEndereco(endereco);

        if(!grupoValidation.gruposEmpty(gruposIds)){
            List<Grupo> grupos = grupoRepository.findAllById(gruposIds);
            if (grupos.size() != gruposIds.size()) {
                throw new ObjetoNaoEncontradoException("Um ou mais grupos não encontrados");
            }
            clienteUpdate.setGrupos(grupos);
        }

        return clienteUpdate;
    }

    @Transactional
    public Cliente activate(UUID id){
        Cliente cliente = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. id: " + id));
        cliente.setAtivo(true);
        return cliente;
    }

    @Transactional
    public void deactivate(UUID id){
        Cliente cliente = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. id: " + id));
        cliente.setAtivo(false);
    }
}
