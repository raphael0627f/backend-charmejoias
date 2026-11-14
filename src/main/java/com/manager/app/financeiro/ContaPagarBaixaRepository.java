package com.manager.app.financeiro;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContaPagarBaixaRepository extends JpaRepository<ContaPagarBaixa, Long> {
    List<ContaPagarBaixa> findByContaPagarIdOrderByIdAsc(Long contaPagarId);

    boolean existsByContaPagarIdAndEstornadaFalse(Long contaPagarId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ContaPagarBaixa b where b.id = :id")
    Optional<ContaPagarBaixa> findByIdForUpdate(@Param("id") Long id);

    // id do título da baixa sem carregar a entidade (para bloquear o título antes de reler a baixa)
    @Query("select b.contaPagar.id from ContaPagarBaixa b where b.id = :id")
    Optional<Long> findContaPagarIdById(@Param("id") Long id);
}
