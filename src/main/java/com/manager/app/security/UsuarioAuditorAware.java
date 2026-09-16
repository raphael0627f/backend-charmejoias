package com.manager.app.security;

import com.manager.app.usuario.Usuario;
import com.manager.app.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

// usado pelo @CreatedBy/@LastModifiedBy (Spring Data JPA auditing) pra resolver o Usuario
// autenticado no momento do save - retorna vazio se não houver ninguém autenticado (ex: seed/migration)
@Component
@RequiredArgsConstructor
public class UsuarioAuditorAware implements AuditorAware<Usuario> {

    private final UsuarioRepository usuarioRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<Usuario> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            return Optional.empty();
        }

        // Esse método é chamado pelo Hibernate de dentro do próprio callback de
        // pre-persist/pre-update, ou seja, no meio de um flush em andamento - toda
        // query do Spring Data JPA tenta fazer auto-flush do que estiver pendente
        // antes de rodar, e como o flush já está em andamento, isso disparava o
        // callback de novo (e de novo...), causando StackOverflowError. Desligar o
        // auto-flush só durante essa consulta evita a recursão.
        FlushModeType flushModeAnterior = entityManager.getFlushMode();
        entityManager.setFlushMode(FlushModeType.COMMIT);
        try {
            return usuarioRepository.findByUsuario(authentication.getName());
        } finally {
            entityManager.setFlushMode(flushModeAnterior);
        }
    }
}
