package com.manager.app.produto;

import com.manager.app.shared.exception.ObjetoExistenteException;
import com.manager.app.cliente.ClienteRepository;
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
