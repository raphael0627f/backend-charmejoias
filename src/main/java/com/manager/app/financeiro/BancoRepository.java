package com.manager.app.financeiro;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BancoRepository extends JpaRepository<Banco, Long> {
    // bloqueio pessimista da linha do banco: serializa movimentações do mesmo banco
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Banco b where b.id = :id")
    Optional<Banco> findByIdForUpdate(@Param("id") Long id);

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);
}
