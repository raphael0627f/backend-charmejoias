package com.manager.app.repository;

import com.manager.app.entity.Categoria;
import com.manager.app.entity.Fornecedor;
import com.manager.app.entity.Grupo;
import com.manager.app.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProdutoRepository  extends JpaRepository<Produto, UUID>, JpaSpecificationExecutor<Produto> {
    Optional<Produto> findByIdAndAtivoTrue(UUID id);
    Optional<Produto> findByCodigoAndAtivoTrue(String codigo);
    List<Produto> findAllByAtivoTrue();
    boolean existsByFornecedor(Fornecedor fornecedor);
    boolean existsByCategoria(Categoria categoria);
    boolean existsByCodigoAndIdNot(String codigo, UUID id);
    boolean existsByCodigo(String codigo);
    boolean existsByGrupos(Grupo grupo);
}
