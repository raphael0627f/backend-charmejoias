package com.manager.app.spc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpcProdutoInsumoOpcionalRepository extends JpaRepository<SpcProdutoInsumoOpcional, Long> {

    List<SpcProdutoInsumoOpcional> findByProdutoCodigoOrderByInsumoNome(Integer produtoCodigo);

    boolean existsByProdutoCodigoAndInsumoCodigo(Integer produtoCodigo, Integer insumoCodigo);
}
