package com.manager.app.infra.validation;

import com.manager.app.infra.exception.ObjetoExistenteException;
import com.manager.app.repository.ClienteRepository;
import com.manager.app.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProdutoValidation {

    private final ProdutoRepository repository;

    public void codigoExistenteCreate(String codigo){
        if(repository.existsByCodigo(codigo)){
            throw new ObjetoExistenteException("Produto existente com este código: " + codigo);
        }
    }
    public void codigoExistenteUpdate(String codigo, UUID id){
        if(repository.existsByCodigoAndIdNot(codigo, id)){
            throw new ObjetoExistenteException("Produto existente com este código: " + codigo);
        }
    }
}
