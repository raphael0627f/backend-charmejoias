package com.manager.app.categoria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CategoriaRepository  extends JpaRepository<Categoria, Long>, JpaSpecificationExecutor<Categoria> {
}
