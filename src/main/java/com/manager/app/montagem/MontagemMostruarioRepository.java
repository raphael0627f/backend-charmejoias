package com.manager.app.montagem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface MontagemMostruarioRepository extends JpaRepository<MontagemMostruario, Long>, JpaSpecificationExecutor<MontagemMostruario> {
    // montagem em que a consignação / o pedido já está vinculado (no máximo uma)
    Optional<MontagemMostruario> findByConsignacoes_Id(Long consignacaoId);
    Optional<MontagemMostruario> findByPedidos_Id(Long pedidoId);
}
