package com.manager.app.acertoconsignacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AcertoConsignacaoRepository extends JpaRepository<AcertoConsignacao, Long>, JpaSpecificationExecutor<AcertoConsignacao> {

    boolean existsByConsignacaoId(Long consignacaoId);

    // usado na listagem de consignações pra marcar "Finalizado" (tem acerto
    // confirmado) vs "Em aberto" — em lote pelos ids da página, não um por um
    @Query("SELECT DISTINCT a.consignacao.id FROM AcertoConsignacao a WHERE a.consignacao.id IN :consignacaoIds AND a.confirmado = true")
    List<Long> findConsignacaoIdsComAcertoConfirmado(@Param("consignacaoIds") List<Long> consignacaoIds);
}
