package com.manager.app.montagem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MontagemMostruarioRepository extends JpaRepository<MontagemMostruario, Long>, JpaSpecificationExecutor<MontagemMostruario> {
}
