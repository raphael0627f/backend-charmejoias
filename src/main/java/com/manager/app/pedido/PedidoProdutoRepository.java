package com.manager.app.pedido;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PedidoProdutoRepository extends JpaRepository<PedidoProduto, Long> {

    @Query("""
            select pp.pedido.id as pedidoId,
                   count(pp.id) as itensCount,
                   coalesce(sum(pp.quantidade), 0) as quantidadeTotal,
                   coalesce(sum(pp.quantidade * pp.preco), 0) as valorTotal
            from PedidoProduto pp
            where pp.pedido.id in :pedidoIds
            group by pp.pedido.id
            """)
    List<PedidoTotaisProjecao> buscarTotais(@Param("pedidoIds") List<Long> pedidoIds);
}
