package com.manager.app.grupo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GrupoRepository  extends JpaRepository<Grupo, Long>, JpaSpecificationExecutor<Grupo> {
    boolean existsByNomeIgnoreCaseAndTipo(String nome, TipoGrupo tipo);
    boolean existsByNomeIgnoreCaseAndTipoAndIdNot(String nome, TipoGrupo tipo, Long id);
}
