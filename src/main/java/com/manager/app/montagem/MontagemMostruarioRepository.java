package com.manager.app.montagem;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface MontagemMostruarioRepository extends JpaRepository<MontagemMostruario, Long>, JpaSpecificationExecutor<MontagemMostruario> {
    // montagem em que a consignação / o pedido já está vinculado (no máximo uma)
    Optional<MontagemMostruario> findByConsignacoes_Id(Long consignacaoId);
    Optional<MontagemMostruario> findByPedidos_Id(Long pedidoId);

    // montagem em andamento do cliente (regra: no máximo uma ABERTA/EM_MONTAGEM por cliente); a segunda consulta
    // ignora a própria montagem, para a troca de cliente
    Optional<MontagemMostruario> findFirstByCliente_IdAndStatusInOrderById(UUID clienteId, Collection<StatusMontagem> status);
    Optional<MontagemMostruario> findFirstByCliente_IdAndStatusInAndIdNotOrderById(UUID clienteId, Collection<StatusMontagem> status, Long montagemId);

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

    // andamento: pedidos (não cancelados) e quantos já estão separados (Separado ou Enviado)
    @Query("select m.id as montagemId, count(p) as total, "
            + "coalesce(sum(case when p.status in (com.manager.app.pedido.StatusPedido.SEPARADO, "
            + "com.manager.app.pedido.StatusPedido.ENVIADO) then 1 else 0 end), 0) as concluidos "
            + "from MontagemMostruario m join m.pedidos p "
            + "where m.id in :ids and p.status <> com.manager.app.pedido.StatusPedido.CANCELADO group by m.id")
    List<MontagemProgressoProjecao> progressoPedidos(@Param("ids") List<Long> ids);

    // andamento: instruções e quantas já foram marcadas como feitas
    @Query("select m.id as montagemId, count(i) as total, "
            + "coalesce(sum(case when i.concluida = true then 1 else 0 end), 0) as concluidos "
            + "from MontagemMostruario m join m.instrucoes i where m.id in :ids group by m.id")
    List<MontagemProgressoProjecao> progressoInstrucoes(@Param("ids") List<Long> ids);

    // andamento: unidades de produtos dos pedidos (mesma regra da conferência: pedidos de produto, sem O.S. e sem
    // cancelados, só itens cadastrados, descontando o cancelado)
    @Query("select m.id as montagemId, coalesce(sum(pp.quantidade - coalesce(pp.quantidadeCancelada, 0)), 0) as total "
            + "from MontagemMostruario m join m.pedidos p join p.produtos pp "
            + "where m.id in :ids and p.status <> com.manager.app.pedido.StatusPedido.CANCELADO "
            + "and p.tipo = com.manager.app.shared.TipoItem.PRODUTO "
            + "and p.categoria <> com.manager.app.pedido.CategoriaPedido.ORDEM_SERVICO "
            + "and pp.produto is not null group by m.id")
    List<MontagemContagemProjecao> necessarioDosPedidos(@Param("ids") List<Long> ids);
}
