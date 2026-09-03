package com.manager.app.cliente;

import com.manager.app.shared.exception.ObjetoExistenteException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ClienteValidation {


    private final ClienteRepository repository;

    public void codigoExistenteCreate(String codigo){
        if(repository.existsByCodigo(codigo)){
            throw new ObjetoExistenteException("Cliente existente com este código: " + codigo);
        }
    }
    public void codigoExistenteUpdate(String codigo, UUID id){
        if(repository.existsByCodigoAndIdNot(codigo, id)){
            throw new ObjetoExistenteException("Cliente existente com este código: " + codigo);
        }
    }
}
