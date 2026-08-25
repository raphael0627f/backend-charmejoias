package com.manager.app.repository;

import com.manager.app.entity.Grupo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GrupoRepository  extends JpaRepository<Grupo, Long>, JpaSpecificationExecutor<Grupo> {
}
