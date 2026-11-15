package com.manager.app.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContaReceberEventoRepository extends JpaRepository<ContaReceberEvento, Long> {
    List<ContaReceberEvento> findByContaReceberIdOrderByIdAsc(Long contaReceberId);
}
