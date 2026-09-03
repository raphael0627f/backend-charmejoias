package com.manager.app.service;

import com.manager.app.entity.Endereco;
import com.manager.app.infra.brasilapi.BrasilApiClient;
import com.manager.app.infra.exception.ObjetoNaoEncontradoException;
import com.manager.app.repository.EnderecoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EnderecoService {

    private final EnderecoRepository repository;
    private final BrasilApiClient brasilApiClient;

    @Transactional
    public Endereco create(Endereco endereco){
        preencherIbgeSeNecessario(endereco);
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
        enderecoUpdate.setIbge(endereco.getIbge());
        preencherIbgeSeNecessario(enderecoUpdate);
        return enderecoUpdate;
    }

    // preenchimento automático via BrasilAPI - se falhar ou o CEP for inválido, fica em branco
    // e é responsabilidade de quem exige o IBGE (ex: emissão de NFe) validar antes de usar
    private void preencherIbgeSeNecessario(Endereco endereco){
        if ((endereco.getIbge() == null || endereco.getIbge().isBlank()) && endereco.getCep() != null) {
            endereco.setIbge(brasilApiClient.buscarCodigoIbgeMunicipio(endereco.getCep()));
        }
    }
}
