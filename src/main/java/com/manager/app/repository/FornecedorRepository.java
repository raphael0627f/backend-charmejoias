package com.manager.app.repository;

import com.manager.app.entity.Fornecedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FornecedorRepository  extends JpaRepository<Fornecedor, Long>, JpaSpecificationExecutor<Fornecedor> {
}
