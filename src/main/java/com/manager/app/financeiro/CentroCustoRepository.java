package com.manager.app.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CentroCustoRepository extends JpaRepository<CentroCusto, Long> {
    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);
}
