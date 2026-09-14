package com.manager.app.spc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpcProdutoRepository extends JpaRepository<SpcProduto, Integer> {

    List<SpcProduto> findByTipoPessoaInOrderByNome(List<TipoPessoaProdutoSpc> tiposPessoa);
}
