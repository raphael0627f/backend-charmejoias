package com.manager.app.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CreditoClienteMovimentoRepository extends JpaRepository<CreditoClienteMovimento, Long> {

    List<CreditoClienteMovimento> findByClienteIdOrderByIdAsc(UUID clienteId);

    List<CreditoClienteMovimento> findByRecebimentoIdAndTipo(Long recebimentoId, CreditoClienteMovimento.Tipo tipo);
}
