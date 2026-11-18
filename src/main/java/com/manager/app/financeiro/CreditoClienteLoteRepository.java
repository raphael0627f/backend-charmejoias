package com.manager.app.financeiro;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditoClienteLoteRepository extends JpaRepository<CreditoClienteLote, Long> {

    List<CreditoClienteLote> findByClienteIdOrderByIdDesc(UUID clienteId);

    // trava os lotes ativos do cliente em ordem de id (ordem única de trava; FIFO da fase 3)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from CreditoClienteLote l where l.cliente.id = :clienteId and l.situacao = com.manager.app.financeiro.CreditoClienteLote.Situacao.ATIVO order by l.id")
    List<CreditoClienteLote> findAtivosForUpdate(@Param("clienteId") UUID clienteId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from CreditoClienteLote l where l.id = :id")
    Optional<CreditoClienteLote> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from CreditoClienteLote l where l.origemTipo = com.manager.app.financeiro.CreditoClienteLote.Origem.EXCEDENTE_ACERTO and l.origemId = :excedenteId and l.situacao = com.manager.app.financeiro.CreditoClienteLote.Situacao.ATIVO")
    Optional<CreditoClienteLote> findAtivoDoExcedenteForUpdate(@Param("excedenteId") Long excedenteId);

    @Query("select coalesce(sum(l.valorOriginal - l.valorUtilizado), 0) from CreditoClienteLote l where l.cliente.id = :clienteId and l.situacao = com.manager.app.financeiro.CreditoClienteLote.Situacao.ATIVO")
    BigDecimal saldo(@Param("clienteId") UUID clienteId);
}
