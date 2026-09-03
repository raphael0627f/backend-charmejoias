package com.manager.app.notafiscal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface NotaFiscalRepository extends JpaRepository<NotaFiscal, Long>, JpaSpecificationExecutor<NotaFiscal> {
    List<NotaFiscal> findAllByStatus(StatusNotaFiscal status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NotaFiscal n where n.id = :id")
    Optional<NotaFiscal> findByIdForUpdate(Long id);
}
