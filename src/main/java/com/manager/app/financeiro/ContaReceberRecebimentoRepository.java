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

    // "valor realmente pago" por título: somas dos recebimentos não estornados (crédito do cliente conta; taxa do cliente à parte)
    @Query("select new com.manager.app.financeiro.TotaisRecebimento(r.contaReceber.id, coalesce(sum(r.valorTotal), 0), coalesce(sum(r.valorPrincipal), 0), "
            + "coalesce(sum(r.juros), 0), coalesce(sum(r.multa), 0), coalesce(sum(r.desconto), 0), coalesce(sum(r.valorTaxaCliente), 0), "
            + "coalesce(sum(case when r.origem = com.manager.app.financeiro.ContaReceberRecebimento.Origem.CREDITO_CLIENTE then r.valorTotal else 0 end), 0)) "
            + "from ContaReceberRecebimento r where r.contaReceber.id in :ids and r.estornada = false group by r.contaReceber.id")
    List<TotaisRecebimento> totaisPorTitulo(@Param("ids") java.util.Collection<Long> ids);

    // baixas automáticas (INFINITEPAY) não estornadas, por título: [tituloId, soma da taxa paga pelo cliente]
    @Query("select r.contaReceber.id, coalesce(sum(r.valorTaxaCliente), 0) from ContaReceberRecebimento r where r.contaReceber.id in :ids "
            + "and r.estornada = false and r.origem = com.manager.app.financeiro.ContaReceberRecebimento.Origem.INFINITEPAY group by r.contaReceber.id")
    List<Object[]> resumoPagoPeloLink(@Param("ids") java.util.Collection<Long> ids);

    // id do título do recebimento sem carregar a entidade (para bloquear o título antes de reler o recebimento)
    @Query("select r.contaReceber.id from ContaReceberRecebimento r where r.id = :id")
    Optional<Long> findContaReceberIdById(@Param("id") Long id);
}
