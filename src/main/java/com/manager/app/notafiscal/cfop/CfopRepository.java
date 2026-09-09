package com.manager.app.notafiscal.cfop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CfopRepository extends JpaRepository<Cfop, String> {
    List<Cfop> findByCodigoStartingWithOrderByCodigo(String prefixo);
}
