package com.manager.app.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ContaPagarAnexoRepository extends JpaRepository<ContaPagarAnexo, Long> {

    List<ContaPagarAnexo> findByContaPagarIdOrderByIdAsc(Long contaPagarId);

    long countByContaPagarId(Long contaPagarId);

    // [id do título, quantidade de anexos]
    @Query("select a.contaPagar.id, count(a) from ContaPagarAnexo a where a.contaPagar.id in :ids group by a.contaPagar.id")
    List<Object[]> contarPorTitulos(@Param("ids") Collection<Long> ids);
}
