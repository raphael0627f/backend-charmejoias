package com.manager.app.infra.validation;

import com.manager.app.infra.exception.ObjetoExistenteException;
import com.manager.app.repository.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmpresaValidation {

    private final EmpresaRepository repository;

    public void cnpjExistenteCreate(String cnpj){
        if(repository.existsByCnpj(cnpj)){
            throw new ObjetoExistenteException("Empresa existente com este CNPJ: " + cnpj);
        }
    }

    public void cnpjExistenteUpdate(String cnpj, Long id){
        if(repository.existsByCnpjAndIdNot(cnpj, id)){
            throw new ObjetoExistenteException("Empresa existente com este CNPJ: " + cnpj);
        }
    }
}
