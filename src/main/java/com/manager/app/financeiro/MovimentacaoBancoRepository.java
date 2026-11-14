package com.manager.app.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface MovimentacaoBancoRepository extends JpaRepository<MovimentacaoBanco, Long> {

    String SALDO = "coalesce(sum(case when m.tipo = com.manager.app.financeiro.TipoMovimentacao.ENTRADA then m.valor else -m.valor end), 0)";

    @Query("select " + SALDO + " from MovimentacaoBanco m where m.banco.id = :bancoId")
    BigDecimal saldoDoBanco(@Param("bancoId") Long bancoId);

    // [bancoId, saldo] dos bancos que têm movimentações
    @Query("select m.banco.id, " + SALDO + " from MovimentacaoBanco m group by m.banco.id")
    List<Object[]> saldosPorBanco();

    boolean existsByBancoIdAndOrigemNot(Long bancoId, OrigemMovimentacao origem);

    boolean existsByBancoId(Long bancoId);

    Optional<MovimentacaoBanco> findFirstByBancoIdAndOrigem(Long bancoId, OrigemMovimentacao origem);

    List<MovimentacaoBanco> findByTransferenciaIdOrderByIdAsc(Long transferenciaId);
}
