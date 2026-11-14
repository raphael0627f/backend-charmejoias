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

    @Query(value = "select nextval('conta_pagar_numero_seq')", nativeQuery = true)
    Long proximoNumero();

    List<ContaPagar> findByGrupoParcelamentoIdOrderByParcelaNumeroAscIdAsc(UUID grupo);

    // [favorecido, quantidade de usos]
    @Query("select c.favorecido, count(c) from ContaPagar c where c.favorecido is not null "
            + "and lower(c.favorecido) like :busca escape '!' group by c.favorecido order by count(c) desc, c.favorecido asc")
    List<Object[]> favorecidos(@Param("busca") String busca, Pageable pageable);

    boolean existsByCategoriaId(Long categoriaId);

    // bloqueio pessimista da linha (SELECT ... FOR UPDATE): serializa baixa, estorno, edição e cancelamento do mesmo título
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContaPagar c where c.id = :id")
    java.util.Optional<ContaPagar> findByIdForUpdate(@Param("id") Long id);
}
