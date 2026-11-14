package com.manager.app.financeiro;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContaPagarRecorrenciaRepository extends JpaRepository<ContaPagarRecorrencia, Long> {

    // bloqueio pessimista da regra: serializa geração, reajuste e encerramento
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ContaPagarRecorrencia r where r.id = :id")
    Optional<ContaPagarRecorrencia> findByIdForUpdate(@Param("id") Long id);

    @Query("select r.id from ContaPagarRecorrencia r where r.ativa = true order by r.id")
    List<Long> idsAtivas();
}
