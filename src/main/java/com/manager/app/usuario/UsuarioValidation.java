package com.manager.app.usuario;

import com.manager.app.shared.exception.ObjetoExistenteException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UsuarioValidation {

    private final UsuarioRepository repository;

    public void usuarioExistenteCreate(String usuario){
        if (repository.existsByUsuario(usuario)) {
            throw new ObjetoExistenteException("Usuário existente com este nome de usuário: " + usuario);
        }
    }

    public void usuarioExistenteUpdate(String usuario, UUID id){
        if (repository.existsByUsuarioAndIdNot(usuario, id)) {
            throw new ObjetoExistenteException("Usuário existente com este nome de usuário: " + usuario);
        }
    }
}
