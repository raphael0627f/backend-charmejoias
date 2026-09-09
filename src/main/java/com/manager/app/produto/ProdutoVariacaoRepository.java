package com.manager.app.produto;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdutoVariacaoRepository extends JpaRepository<ProdutoVariacao, Long> {
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, Long id);
    boolean existsByCodigoBarras(String codigoBarras);
}
