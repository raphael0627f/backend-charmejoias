package com.manager.app.produto;

import com.manager.app.categoria.Categoria;
import com.manager.app.fornecedor.Fornecedor;
import com.manager.app.grupo.Grupo;
import com.manager.app.ncm.Ncm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProdutoRepository  extends JpaRepository<Produto, UUID>, JpaSpecificationExecutor<Produto> {
    Optional<Produto> findByIdAndAtivoTrue(UUID id);
    Optional<Produto> findBySkuAndAtivoTrue(String sku);
    List<Produto> findAllByAtivoTrue();
    boolean existsByFornecedor(Fornecedor fornecedor);
    boolean existsByCategoria(Categoria categoria);
    boolean existsByNcm(Ncm ncm);
    boolean existsBySkuAndIdNot(String sku, UUID id);
    boolean existsBySku(String sku);
    boolean existsByGrupos(Grupo grupo);

    @Query("SELECT p.sku FROM Produto p")
    List<String> findAllSkus();
}
