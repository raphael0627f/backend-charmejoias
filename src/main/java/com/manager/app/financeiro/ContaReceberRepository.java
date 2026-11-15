package com.manager.app.financeiro;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContaReceberRepository extends JpaRepository<ContaReceber, Long>, JpaSpecificationExecutor<ContaReceber> {

    @Query(value = "select nextval('conta_receber_numero_seq')", nativeQuery = true)
    Long proximoNumero();

    List<ContaReceber> findByGrupoParcelamentoIdOrderByParcelaNumeroAscIdAsc(UUID grupo);

    boolean existsByCategoriaId(Long categoriaId);

    // bloqueio pessimista da linha (SELECT ... FOR UPDATE): serializa edição, cancelamento e (fase 3) recebimento do mesmo título
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContaReceber c where c.id = :id")
    Optional<ContaReceber> findByIdForUpdate(@Param("id") Long id);
}
