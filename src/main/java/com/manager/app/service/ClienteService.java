package com.manager.app.service;

import com.manager.app.entity.Cliente;
import com.manager.app.infra.exception.ObjetoNaoEncontradoException;
import com.manager.app.infra.validation.ClienteValidation;
import com.manager.app.repository.ClienteRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository repository;
    private final ClienteValidation validation;

    public List<Cliente> findAll(){
        return repository.findAll();
    }

    public Cliente findById(String id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. id: " + id));
    }

    public Cliente findByCodigo(String codigo){
        return repository.findByCodigo(codigo).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. codigo: " + codigo));
    }

    @Transactional
    public Cliente create(Cliente cliente){
        validation.codigoExistenteCreate(cliente.getCodigo());
        return repository.save(cliente);
    }

    public Cliente update(Cliente cliente){
        validation.codigoExistenteUpdate(cliente.getCodigo(), cliente.getId());
        Cliente clienteUpdate = repository.findById(cliente.getId()).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. id: " + cliente.getId()));
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
        return clienteUpdate;
    }

    @Transactional
    public Cliente activate(String id){
        Cliente cliente = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. id: " + id));
        cliente.setAtivo(true);
        return cliente;
    }

    @Transactional
    public void deactivate(String id){
        Cliente cliente = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. id: " + id));
        cliente.setAtivo(false);
    }
}
