package com.manager.app.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriaReceitaRepository extends JpaRepository<CategoriaReceita, Long> {

    // nome único dentro do mesmo pai (grupos têm pai nulo)
    boolean existsByNomeIgnoreCaseAndPaiIsNullAndIdNot(String nome, Long id);

    boolean existsByNomeIgnoreCaseAndPaiIsNull(String nome);

    boolean existsByNomeIgnoreCaseAndPaiIdAndIdNot(String nome, Long paiId, Long id);

    boolean existsByNomeIgnoreCaseAndPaiId(String nome, Long paiId);

    List<CategoriaReceita> findByPaiId(Long paiId);

    boolean existsByPaiId(Long paiId);

    // subgrupo pelo nome e pelo nome do grupo (usado na categoria fixa Vendas > Consignação)
    java.util.Optional<CategoriaReceita> findFirstByNomeIgnoreCaseAndPaiNomeIgnoreCase(String nome, String nomePai);

    boolean existsByPaiIdAndAtivoTrue(Long paiId);
}
