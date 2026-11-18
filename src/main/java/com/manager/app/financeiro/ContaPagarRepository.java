package com.manager.app.financeiro;

import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ContaPagarRepository extends JpaRepository<ContaPagar, Long>, JpaSpecificationExecutor<ContaPagar> {

    // título a pagar ativo (não cancelado) da devolução de um excedente (sem trava)
    @Query("select c from ContaPagar c where c.excedenteId = :excedenteId and c.situacao <> com.manager.app.financeiro.SituacaoContaPagar.CANCELADO")
    java.util.Optional<ContaPagar> findAtivoDoExcedente(@org.springframework.data.repository.query.Param("excedenteId") Long excedenteId);

    // somente o id (não carrega a entidade: quem trava o título depois precisa lê-lo já atualizado)
    @Query("select c.id from ContaPagar c where c.excedenteId = :excedenteId and c.situacao <> com.manager.app.financeiro.SituacaoContaPagar.CANCELADO")
    java.util.Optional<Long> findIdAtivoDoExcedente(@org.springframework.data.repository.query.Param("excedenteId") Long excedenteId);

    @Query(value = "select nextval('conta_pagar_numero_seq')", nativeQuery = true)
    Long proximoNumero();

    List<ContaPagar> findByGrupoParcelamentoIdOrderByParcelaNumeroAscIdAsc(UUID grupo);

    // [favorecido, quantidade de usos]
    @Query("select c.favorecido, count(c) from ContaPagar c where c.favorecido is not null "
            + "and lower(c.favorecido) like :busca escape '!' group by c.favorecido order by count(c) desc, c.favorecido asc")
    List<Object[]> favorecidos(@Param("busca") String busca, Pageable pageable);

    boolean existsByCategoriaId(Long categoriaId);

    List<ContaPagar> findByRecorrenciaIdOrderByOcorrenciaDataAscIdAsc(Long recorrenciaId);

    @Query("select c.ocorrenciaData from ContaPagar c where c.recorrenciaId = :id")
    List<java.time.LocalDate> ocorrencias(@Param("id") Long recorrenciaId);

    @Query("select c.id from ContaPagar c where c.recorrenciaId = :id order by c.id")
    List<Long> idsDaRecorrencia(@Param("id") Long recorrenciaId);

    List<ContaPagar> findTop6ByRecorrenciaIdAndSituacaoNotAndVencimentoGreaterThanEqualOrderByVencimentoAscIdAsc(Long recorrenciaId, SituacaoContaPagar situacao, java.time.LocalDate vencimento);

    long countByRecorrenciaId(Long recorrenciaId);

    long countByRecorrenciaIdAndOcorrenciaDataLessThanEqual(Long recorrenciaId, java.time.LocalDate data);

    // bloqueio pessimista da linha (SELECT ... FOR UPDATE): serializa pagamento, estorno, edição e cancelamento do mesmo título
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContaPagar c where c.id = :id")
    java.util.Optional<ContaPagar> findByIdForUpdate(@Param("id") Long id);
}
