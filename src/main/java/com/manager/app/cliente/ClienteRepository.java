package com.manager.app.cliente;

import com.manager.app.grupo.Grupo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClienteRepository  extends JpaRepository<Cliente, UUID>, JpaSpecificationExecutor<Cliente> {
    Optional<Cliente> findByIdAndAtivoTrue(UUID id);
    Optional<Cliente> findByCodigo(String codigo);
    List<Cliente> findAllByAtivoTrue();
    boolean existsByCodigoAndIdNot(String codigo, UUID id);
    boolean existsByCodigo(String codigo);
    boolean existsByGrupos(Grupo grupo);
}
