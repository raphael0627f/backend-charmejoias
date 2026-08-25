package com.manager.app.repository;

import com.manager.app.entity.Categoria;
import com.manager.app.entity.Grupo;
import com.manager.app.entity.Joia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JoiaRepository extends JpaRepository<Joia, UUID>, JpaSpecificationExecutor<Joia> {
    Optional<Joia> findByIdAndAtivoTrue(UUID id);
    Optional<Joia> findByCodigoAndAtivoTrue(String codigo);
    List<Joia> findAllByAtivoTrue();
    boolean existsByCategoria(Categoria categoria);
    boolean existsByCodigoAndIdNot(String codigo, UUID id);
    boolean existsByCodigo(String codigo);
    boolean existsByGrupos(Grupo grupo);
}
