package com.manager.app.financeiro;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContaPagarPagamentoRepository extends JpaRepository<ContaPagarPagamento, Long> {
    List<ContaPagarPagamento> findByContaPagarIdOrderByIdAsc(Long contaPagarId);

    boolean existsByContaPagarIdAndEstornadaFalse(Long contaPagarId);

    // títulos (entre os informados) que possuem pagamento ativo
    @Query("select distinct b.contaPagar.id from ContaPagarPagamento b where b.contaPagar.id in :ids and b.estornada = false")
    List<Long> titulosComPagamentoAtivo(@Param("ids") java.util.Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ContaPagarPagamento b where b.id = :id")
    Optional<ContaPagarPagamento> findByIdForUpdate(@Param("id") Long id);

    // id do título do pagamento sem carregar a entidade (para bloquear o título antes de reler o pagamento)
    @Query("select b.contaPagar.id from ContaPagarPagamento b where b.id = :id")
    Optional<Long> findContaPagarIdById(@Param("id") Long id);
}
