package com.manager.app.montagem;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MontagemMostruarioRepository extends JpaRepository<MontagemMostruario, Long>, JpaSpecificationExecutor<MontagemMostruario> {
    // montagem em que a consignação / o pedido já está vinculado (no máximo uma)
    Optional<MontagemMostruario> findByConsignacoes_Id(Long consignacaoId);
    Optional<MontagemMostruario> findByPedidos_Id(Long pedidoId);

    // listagem: traz cliente e mostruário na mesma consulta (sem uma consulta extra por linha)
    @Override
    @EntityGraph(attributePaths = {"cliente", "mostruario"})
    Page<MontagemMostruario> findAll(Specification<MontagemMostruario> spec, Pageable pageable);

    // quantidades por montagem (uma consulta para a página inteira), em vez de carregar as listas de cada uma
    @Query("select m.id as montagemId, count(c) as total from MontagemMostruario m join m.consignacoes c "
            + "where m.id in :ids group by m.id")
    List<MontagemContagemProjecao> contarConsignacoes(@Param("ids") List<Long> ids);

    // pedidos cancelados continuam vinculados, mas não entram na contagem
    @Query("select m.id as montagemId, count(p) as total from MontagemMostruario m join m.pedidos p "
            + "where m.id in :ids and p.status <> com.manager.app.pedido.StatusPedido.CANCELADO group by m.id")
    List<MontagemContagemProjecao> contarPedidos(@Param("ids") List<Long> ids);
}
