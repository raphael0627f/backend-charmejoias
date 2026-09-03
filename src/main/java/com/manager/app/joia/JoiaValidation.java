package com.manager.app.joia;

import com.manager.app.shared.exception.ObjetoExistenteException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JoiaValidation {

    private final JoiaRepository repository;

    public void codigoExistenteCreate(String codigo){
        if(repository.existsByCodigo(codigo)){
            throw new ObjetoExistenteException("Joia existente com este código: " + codigo);
        }
    }
    public void codigoExistenteUpdate(String codigo, UUID id){
        if(repository.existsByCodigoAndIdNot(codigo, id)){
            throw new ObjetoExistenteException("Joia existente com este código: " + codigo);
        }
    }
}
