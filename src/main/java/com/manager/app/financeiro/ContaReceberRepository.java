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

    // títulos próprios de um documento de consignação (todas as situações), em ordem de criação
    List<ContaReceber> findByPreAcertoIdOrderByIdAsc(Long preAcertoId);

    List<ContaReceber> findByAcertoIdOrderByIdAsc(Long acertoId);

    // títulos ativos (não cancelados) da consignação, com bloqueio pessimista: serializa com o cancelamento de cada título
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContaReceber c where c.consignacao.id = :consignacaoId and c.situacao <> com.manager.app.financeiro.SituacaoContaReceber.CANCELADO order by c.id")
    List<ContaReceber> findAtivosDaConsignacaoForUpdate(@Param("consignacaoId") Long consignacaoId);

    // todos os títulos da consignação, inclusive cancelados (resumo financeiro: visão completa da consignação)
    @Query("select c from ContaReceber c where c.consignacao.id = :consignacaoId order by c.vencimento, c.numero")
    List<ContaReceber> findTodosDaConsignacao(@Param("consignacaoId") Long consignacaoId);

    // somente leitura (resumo)
    @Query("select c from ContaReceber c where c.consignacao.id = :consignacaoId and c.situacao <> com.manager.app.financeiro.SituacaoContaReceber.CANCELADO order by c.id")
    List<ContaReceber> findAtivosDaConsignacao(@Param("consignacaoId") Long consignacaoId);

    @Query("select count(c) > 0 from ContaReceber c where c.preAcerto.id = :id and c.situacao <> com.manager.app.financeiro.SituacaoContaReceber.CANCELADO")
    boolean existsPreAcertoAtivo(@Param("id") Long preAcertoId);

    @Query("select count(c) > 0 from ContaReceber c where c.acerto.id = :id and c.situacao <> com.manager.app.financeiro.SituacaoContaReceber.CANCELADO")
    boolean existsAcertoAtivo(@Param("id") Long acertoId);

    // bloqueio pessimista da linha (SELECT ... FOR UPDATE): serializa edição, cancelamento e (fase 3) recebimento do mesmo título
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContaReceber c where c.id = :id")
    Optional<ContaReceber> findByIdForUpdate(@Param("id") Long id);
}
