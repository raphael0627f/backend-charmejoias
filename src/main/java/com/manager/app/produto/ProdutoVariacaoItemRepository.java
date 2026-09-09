package com.manager.app.produto;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdutoVariacaoItemRepository extends JpaRepository<ProdutoVariacaoItem, Long> {
    boolean existsByItemVariacao_VariacaoIdAndProdutoVariacao_AtivoTrue(Long variacaoId);
    boolean existsByItemVariacaoId(Long itemVariacaoId);
}
