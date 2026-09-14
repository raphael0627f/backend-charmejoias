package com.manager.app.consignacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConsignacaoProdutoRepository extends JpaRepository<ConsignacaoProduto, Long> {

    // totais agregados direto no banco - usado pela listagem, que não precisa (e não deve)
    // carregar os produtos/joias completos de cada consignação, só pra somar quantidade/valor
    @Query("""
            select cp.consignacao.id as consignacaoId,
                   count(cp.id) as itensCount,
                   coalesce(sum(cp.quantidade), 0) as quantidadeTotal,
                   coalesce(sum(cp.quantidade * cp.preco), 0) as valorTotal
            from ConsignacaoProduto cp
            where cp.consignacao.id in :consignacaoIds
            group by cp.consignacao.id
            """)
    List<ConsignacaoTotaisProjecao> buscarTotais(@Param("consignacaoIds") List<Long> consignacaoIds);

    // os joins de categoria precisam ser explícitos (left join p.categoria / left join j.categoria):
    // navegação implícita (p.categoria.nome) gera INNER JOIN por baixo dos panos, e como produto e
    // joia são mutuamente exclusivos por linha, isso descartava toda linha de produto puro (joia
    // sempre null) ou de joia pura (produto sempre null) do resultado inteiro
    @Query("""
            select cp.consignacao.id as consignacaoId,
                   coalesce(pc.nome, jc.nome, 'Sem categoria') as categoria,
                   sum(cp.quantidade) as quantidade
            from ConsignacaoProduto cp
            left join cp.produto p
            left join p.categoria pc
            left join cp.joia j
            left join j.categoria jc
            where cp.consignacao.id in :consignacaoIds
            group by cp.consignacao.id, coalesce(pc.nome, jc.nome, 'Sem categoria')
            """)
    List<CategoriaQuantidadeProjecao> buscarQuantidadePorCategoria(@Param("consignacaoIds") List<Long> consignacaoIds);
}
