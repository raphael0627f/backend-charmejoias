package com.manager.app.service;

import com.manager.app.entity.Cliente;
import com.manager.app.entity.Endereco;
import com.manager.app.entity.Grupo;
import com.manager.app.infra.exception.ObjetoNaoEncontradoException;
import com.manager.app.infra.validation.ClienteValidation;
import com.manager.app.infra.validation.GrupoValidation;
import com.manager.app.repository.ClienteRepository;
import com.manager.app.repository.GrupoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    public List<Cliente> findAll(){
        return repository.findAll();
    }

    public Cliente findById(UUID id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. id: " + id));
    }

    public Cliente findByCodigo(String codigo){
        return repository.findByCodigo(codigo).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. codigo: " + codigo));
    }

    @Transactional
    public Cliente create(Cliente cliente, List<Long> gruposIds){
        validation.codigoExistenteCreate(cliente.getCodigo());

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
