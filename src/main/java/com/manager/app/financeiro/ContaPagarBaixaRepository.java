package com.manager.app.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContaPagarBaixaRepository extends JpaRepository<ContaPagarBaixa, Long> {
    List<ContaPagarBaixa> findByContaPagarIdOrderByIdAsc(Long contaPagarId);

    boolean existsByContaPagarIdAndEstornadaFalse(Long contaPagarId);
}
