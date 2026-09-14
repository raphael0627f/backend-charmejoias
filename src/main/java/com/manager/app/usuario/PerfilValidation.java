package com.manager.app.usuario;

import com.manager.app.shared.exception.ObjetoExistenteException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PerfilValidation {

    private final PerfilRepository repository;

    public void nomeExistenteCreate(String nome){
        if (repository.existsByNome(nome)) {
            throw new ObjetoExistenteException("Perfil existente com este nome: " + nome);
        }
    }

    public void nomeExistenteUpdate(String nome, Long id){
        if (repository.existsByNomeAndIdNot(nome, id)) {
            throw new ObjetoExistenteException("Perfil existente com este nome: " + nome);
        }
    }
}
