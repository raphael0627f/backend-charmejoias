package com.manager.app.infra.validation;

import com.manager.app.infra.exception.ObjetoExistenteException;
import com.manager.app.repository.JoiaRepository;
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
