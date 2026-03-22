package com.manager.app.repository;

import com.manager.app.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository  extends JpaRepository<Cliente, String> {
    Optional<Cliente> findByCodigo(String codigo);
    boolean existsByCodigoAndIdNot(String codigo, String id);
    boolean existsByCodigo(String codigo);
    List<Cliente> findAllByAtivoTrue();
}
