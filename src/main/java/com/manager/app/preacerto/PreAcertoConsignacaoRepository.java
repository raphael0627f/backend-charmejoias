package com.manager.app.preacerto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PreAcertoConsignacaoRepository extends JpaRepository<PreAcertoConsignacao, Long>, JpaSpecificationExecutor<PreAcertoConsignacao> {

    boolean existsByConsignacaoId(Long consignacaoId);

    boolean existsByConsignacaoIdAndIdNot(Long consignacaoId, Long idAtual);
}
