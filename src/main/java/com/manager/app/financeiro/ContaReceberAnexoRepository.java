package com.manager.app.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ContaReceberAnexoRepository extends JpaRepository<ContaReceberAnexo, Long> {

    List<ContaReceberAnexo> findByContaReceberIdOrderByIdAsc(Long contaReceberId);

    long countByContaReceberId(Long contaReceberId);

    // [id do título, quantidade de anexos]
    @Query("select a.contaReceber.id, count(a) from ContaReceberAnexo a where a.contaReceber.id in :ids group by a.contaReceber.id")
    List<Object[]> contarPorTitulos(@Param("ids") Collection<Long> ids);
}
