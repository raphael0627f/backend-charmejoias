package com.manager.app.repository;

import com.manager.app.entity.Cliente;
import com.manager.app.entity.Grupo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClienteRepository  extends JpaRepository<Cliente, UUID> {
    Optional<Cliente> findByCodigo(String codigo);
    List<Cliente> findAllByAtivoTrue();
    boolean existsByCodigoAndIdNot(String codigo, UUID id);
    boolean existsByCodigo(String codigo);
    boolean existsByGrupos(Grupo grupo);
}
