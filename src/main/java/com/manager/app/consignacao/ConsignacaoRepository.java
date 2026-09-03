package com.manager.app.consignacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ConsignacaoRepository  extends JpaRepository<Consignacao, Long>, JpaSpecificationExecutor<Consignacao> {
}
