package com.manager.app.produto;

import com.manager.app.categoria.Categoria;
import com.manager.app.fornecedor.Fornecedor;
import com.manager.app.grupo.Grupo;
import com.manager.app.ncm.Ncm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    boolean existsByCodigoBarras(String codigoBarras);

    @Query("SELECT p.sku FROM Produto p")
    List<String> findAllSkus();

    // busca exata pra bipagem de código de barras: produto ativo cujo próprio sku/codigoBarras bate,
    // ou que tenha uma variação ativa cujo sku/codigoBarras bate - left join pra não excluir produtos
    // simples (sem variações) da comparação
    @Query("""
            select distinct p from Produto p
            left join p.variacoes v
            where p.ativo = true
            and (
                upper(p.sku) = upper(:termo)
                or upper(p.codigoBarras) = upper(:termo)
                or (v.ativo = true and (upper(v.sku) = upper(:termo) or upper(v.codigoBarras) = upper(:termo)))
            )
            """)
    List<Produto> buscarExataPorSkuOuCodigoBarras(@Param("termo") String termo);

    @Query("select p from Produto p where p.ativo = true and upper(p.referencia) = upper(:termo)")
    List<Produto> buscarExataPorReferencia(@Param("termo") String termo);
}
