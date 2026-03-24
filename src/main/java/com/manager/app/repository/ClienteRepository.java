package com.manager.app.repository;

import com.manager.app.entity.Cliente;
import com.manager.app.entity.Grupo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository  extends JpaRepository<Cliente, String> {
    Optional<Cliente> findByCodigo(String codigo);
    boolean existsByCodigoAndIdNot(String codigo, String id);
    boolean existsByCodigo(String codigo);
    boolean existsByGrupo(Grupo grupo);
    List<Cliente> findAllByAtivoTrue();
}
