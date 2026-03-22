package com.manager.app.repository;

import com.manager.app.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdutoRepository  extends JpaRepository<Produto, String> {
}
