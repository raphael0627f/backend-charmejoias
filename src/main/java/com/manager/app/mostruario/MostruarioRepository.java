package com.manager.app.mostruario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MostruarioRepository extends JpaRepository<Mostruario, Long>, JpaSpecificationExecutor<Mostruario> {
    boolean existsByDescricao(String descricao);
    boolean existsByDescricaoAndIdNot(String descricao, Long id);
}
