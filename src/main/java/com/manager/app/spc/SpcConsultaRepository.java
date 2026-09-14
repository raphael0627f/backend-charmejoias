package com.manager.app.spc;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SpcConsultaRepository extends JpaRepository<SpcConsulta, UUID>, JpaSpecificationExecutor<SpcConsulta> {
}
