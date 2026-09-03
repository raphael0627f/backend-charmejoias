package com.manager.app.acerto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AcertoRepository extends JpaRepository<Acerto, Long>, JpaSpecificationExecutor<Acerto> {
}
