package com.manager.app.acertoconsignacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AcertoConsignacaoRepository extends JpaRepository<AcertoConsignacao, Long>, JpaSpecificationExecutor<AcertoConsignacao> {

    boolean existsByConsignacaoId(Long consignacaoId);
}
