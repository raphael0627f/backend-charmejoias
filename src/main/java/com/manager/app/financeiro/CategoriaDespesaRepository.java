package com.manager.app.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriaDespesaRepository extends JpaRepository<CategoriaDespesa, Long> {

    // nome único dentro do mesmo pai (grupos têm pai nulo)
    boolean existsByNomeIgnoreCaseAndPaiIsNullAndIdNot(String nome, Long id);

    boolean existsByNomeIgnoreCaseAndPaiIsNull(String nome);

    boolean existsByNomeIgnoreCaseAndPaiIdAndIdNot(String nome, Long paiId, Long id);

    boolean existsByNomeIgnoreCaseAndPaiId(String nome, Long paiId);

    List<CategoriaDespesa> findByPaiId(Long paiId);

    java.util.Optional<CategoriaDespesa> findByNomeIgnoreCaseAndPaiIsNull(String nome);

    boolean existsByPaiId(Long paiId);

    boolean existsByPaiIdAndAtivoTrue(Long paiId);
}
