package com.manager.app.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContaPagarEventoRepository extends JpaRepository<ContaPagarEvento, Long> {
    List<ContaPagarEvento> findByContaPagarIdOrderByIdAsc(Long contaPagarId);
}
