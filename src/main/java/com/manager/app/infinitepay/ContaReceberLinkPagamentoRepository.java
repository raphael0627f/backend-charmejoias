package com.manager.app.infinitepay;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ContaReceberLinkPagamentoRepository extends JpaRepository<ContaReceberLinkPagamento, Long> {

    List<ContaReceberLinkPagamento> findByContaReceberIdOrderByIdDesc(Long contaReceberId);

    List<ContaReceberLinkPagamento> findByContaReceberIdAndStatus(Long contaReceberId, StatusLinkPagamento status);

    List<ContaReceberLinkPagamento> findByContaReceberIdAndStatusAndCriadoEmAfter(Long contaReceberId, StatusLinkPagamento status, LocalDateTime depoisDe);

    @Query("select l.id from ContaReceberLinkPagamento l where l.orderNsu = :orderNsu")
    java.util.Optional<Long> findIdByOrderNsu(@Param("orderNsu") String orderNsu);

    @Query("select l.contaReceber.id from ContaReceberLinkPagamento l where l.id = :id")
    java.util.Optional<Long> findContaReceberIdById(@Param("id") Long id);
}
