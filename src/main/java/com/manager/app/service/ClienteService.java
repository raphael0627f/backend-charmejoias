package com.manager.app.service;

import com.manager.app.entity.Cliente;
import com.manager.app.infra.exception.ObjetoNaoEncontradoException;
import com.manager.app.infra.validation.ClienteValidation;
import com.manager.app.repository.ClienteRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Transactional
    public void deactivate(String id){
        Cliente cliente = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Cliente não encontrado. id: " + id));
        if (!cliente.isAtivo()) {
            return;
        }
        cliente.setAtivo(false);
    }
}
