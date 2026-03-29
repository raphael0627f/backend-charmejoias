package com.manager.app.repository;

import com.manager.app.entity.Categoria;
import com.manager.app.entity.Fornecedor;
import com.manager.app.entity.Grupo;
import com.manager.app.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProdutoRepository  extends JpaRepository<Produto, UUID> {
    Optional<Produto> findByCodigo(String codigo);
    List<Produto> findAllByAtivoTrue();
    boolean existsByFornecedor(Fornecedor fornecedor);
    boolean existsByCategoria(Categoria categoria);
    boolean existsByCodigoAndIdNot(String codigo, UUID id);
    boolean existsByCodigo(String codigo);
    boolean existsByGrupos(Grupo grupo);
}
