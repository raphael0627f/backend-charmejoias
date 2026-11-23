package com.manager.app.infinitepay;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ContaReceberLinkPagamentoRepository extends JpaRepository<ContaReceberLinkPagamento, Long> {

    List<ContaReceberLinkPagamento> findByContaReceberIdOrderByIdDesc(Long contaReceberId);

    // títulos (dentre os informados) com link de baixa automática ainda ativo
    @Query("select distinct l.contaReceber.id from ContaReceberLinkPagamento l where l.contaReceber.id in :ids "
            + "and l.status = com.manager.app.infinitepay.StatusLinkPagamento.ATIVO and l.modo = com.manager.app.infinitepay.ModoLinkPagamento.AUTOMATICO")
    List<Long> findTitulosComLinkAutomaticoAtivo(@Param("ids") java.util.Collection<Long> ids);

    // links de baixa automática ainda ativos (aguardando pagamento)
    @Query("select count(l) from ContaReceberLinkPagamento l where l.status = com.manager.app.infinitepay.StatusLinkPagamento.ATIVO "
            + "and l.modo = com.manager.app.infinitepay.ModoLinkPagamento.AUTOMATICO")
    long contarAutomaticosAtivos();

    List<ContaReceberLinkPagamento> findByContaReceberIdAndStatus(Long contaReceberId, StatusLinkPagamento status);

    List<ContaReceberLinkPagamento> findByContaReceberIdAndStatusAndCriadoEmAfter(Long contaReceberId, StatusLinkPagamento status, LocalDateTime depoisDe);

    @Query("select l.id from ContaReceberLinkPagamento l where l.orderNsu = :orderNsu")
    java.util.Optional<Long> findIdByOrderNsu(@Param("orderNsu") String orderNsu);

    @Query("select l.contaReceber.id from ContaReceberLinkPagamento l where l.id = :id")
    java.util.Optional<Long> findContaReceberIdById(@Param("id") Long id);
}
