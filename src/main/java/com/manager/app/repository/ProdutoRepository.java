package com.manager.app.repository;

import com.manager.app.entity.Fornecedor;
import com.manager.app.entity.Grupo;
import com.manager.app.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdutoRepository  extends JpaRepository<Produto, String> {
    boolean existsByGrupo(Grupo grupo);
    boolean existsByFornecedor(Fornecedor fornecedor);
}
