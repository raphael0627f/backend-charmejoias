package com.manager.app.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContaPagarRecorrenciaEventoRepository extends JpaRepository<ContaPagarRecorrenciaEvento, Long> {
    List<ContaPagarRecorrenciaEvento> findByRecorrenciaIdOrderByIdAsc(Long recorrenciaId);
}
