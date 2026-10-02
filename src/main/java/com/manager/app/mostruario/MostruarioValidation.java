package com.manager.app.mostruario;

import com.manager.app.shared.exception.ObjetoExistenteException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MostruarioValidation {

    private final MostruarioRepository repository;

    public void descricaoExistenteCreate(String descricao){
        if (repository.existsByDescricao(descricao)) {
            throw new ObjetoExistenteException("Mostruário existente com esta descrição: " + descricao);
        }
    }

    public void descricaoExistenteUpdate(String descricao, Long id){
        if (repository.existsByDescricaoAndIdNot(descricao, id)) {
            throw new ObjetoExistenteException("Mostruário existente com esta descrição: " + descricao);
        }
    }
}
