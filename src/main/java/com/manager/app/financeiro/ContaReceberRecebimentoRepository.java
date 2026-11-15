package com.manager.app.financeiro;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContaReceberRecebimentoRepository extends JpaRepository<ContaReceberRecebimento, Long> {

    List<ContaReceberRecebimento> findByContaReceberIdOrderByIdAsc(Long contaReceberId);

    boolean existsByContaReceberIdAndEstornadaFalse(Long contaReceberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ContaReceberRecebimento r where r.id = :id")
    Optional<ContaReceberRecebimento> findByIdForUpdate(@Param("id") Long id);

    // id do título do recebimento sem carregar a entidade (para bloquear o título antes de reler o recebimento)
    @Query("select r.contaReceber.id from ContaReceberRecebimento r where r.id = :id")
    Optional<Long> findContaReceberIdById(@Param("id") Long id);
}
