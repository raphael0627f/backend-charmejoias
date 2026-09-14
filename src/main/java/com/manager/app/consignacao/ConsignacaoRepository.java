package com.manager.app.consignacao;

import com.manager.app.mostruario.Mostruario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ConsignacaoRepository  extends JpaRepository<Consignacao, Long>, JpaSpecificationExecutor<Consignacao> {
    boolean existsByMostruario(Mostruario mostruario);
}
