package com.manager.app.produto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProdutoVariacaoRepository extends JpaRepository<ProdutoVariacao, Long> {
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, Long id);
    boolean existsBySkuAndProdutoIdNot(String sku, java.util.UUID produtoId);
    boolean existsByCodigoBarras(String codigoBarras);

    @Query("SELECT v.sku FROM ProdutoVariacao v")
    List<String> findAllSkus();
}
