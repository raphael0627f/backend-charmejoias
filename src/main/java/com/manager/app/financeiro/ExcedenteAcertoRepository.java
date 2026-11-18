package com.manager.app.financeiro;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExcedenteAcertoRepository extends JpaRepository<ExcedenteAcerto, Long>, JpaSpecificationExecutor<ExcedenteAcerto> {

    Optional<ExcedenteAcerto> findByAcertoId(Long acertoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ExcedenteAcerto e where e.consignacao.id = :consignacaoId")
    Optional<ExcedenteAcerto> findByConsignacaoIdForUpdate(@Param("consignacaoId") Long consignacaoId);
}
