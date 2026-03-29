package com.manager.app.service;

import com.manager.app.entity.Endereco;
import com.manager.app.infra.exception.ObjetoNaoEncontradoException;
import com.manager.app.repository.EnderecoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EnderecoService {

    private final EnderecoRepository repository;

    @Transactional
    public Endereco create(Endereco endereco){
        return repository.save(endereco);
    }

    @Transactional
    public Endereco update(Endereco endereco){
        Endereco enderecoUpdate = repository.findById(endereco.getId()).orElseThrow(() -> new ObjetoNaoEncontradoException("Endereço não encontrado. id: " + endereco.getId()));
        enderecoUpdate.setLogradouro(endereco.getLogradouro());
        enderecoUpdate.setNumero(endereco.getNumero());
        enderecoUpdate.setComplemento(endereco.getComplemento());
        enderecoUpdate.setBairro(endereco.getBairro());
        enderecoUpdate.setMunicipio(endereco.getMunicipio());
        enderecoUpdate.setUf(endereco.getUf());
        enderecoUpdate.setCep(endereco.getCep());
        return enderecoUpdate;
    }
}
