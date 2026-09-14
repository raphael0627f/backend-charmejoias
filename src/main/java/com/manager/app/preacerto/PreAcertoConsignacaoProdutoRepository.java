package com.manager.app.preacerto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PreAcertoConsignacaoProdutoRepository extends JpaRepository<PreAcertoConsignacaoProduto, Long> {

    // totais agregados direto no banco - mesma técnica usada em ConsignacaoProdutoRepository.buscarTotais,
    // pra listagem não precisar carregar os produtos/joias completos de cada pré-acerto
    @Query("""
            select pap.preAcerto.id as preAcertoId,
                   count(pap.id) as itensCount,
                   coalesce(sum(pap.quantidade), 0) as quantidadeTotal,
                   coalesce(sum(pap.quantidade * pap.preco), 0) as valorTotal
            from PreAcertoConsignacaoProduto pap
            where pap.preAcerto.id in :preAcertoIds
            group by pap.preAcerto.id
            """)
    List<PreAcertoConsignacaoTotaisProjecao> buscarTotais(@Param("preAcertoIds") List<Long> preAcertoIds);

    // os joins de categoria precisam ser explícitos (left join p.categoria / left join j.categoria):
    // navegação implícita (p.categoria.nome) gera INNER JOIN por baixo dos panos, e como produto e
    // joia são mutuamente exclusivos por linha, isso descartava toda linha de produto puro (joia
    // sempre null) ou de joia pura (produto sempre null) do resultado inteiro
    @Query("""
            select pap.preAcerto.id as preAcertoId,
                   coalesce(pc.nome, jc.nome, 'Sem categoria') as categoria,
                   sum(pap.quantidade) as quantidade
            from PreAcertoConsignacaoProduto pap
            left join pap.produto p
            left join p.categoria pc
            left join pap.joia j
            left join j.categoria jc
            where pap.preAcerto.id in :preAcertoIds
            group by pap.preAcerto.id, coalesce(pc.nome, jc.nome, 'Sem categoria')
            """)
    List<PreAcertoCategoriaQuantidadeProjecao> buscarQuantidadePorCategoria(@Param("preAcertoIds") List<Long> preAcertoIds);
}
