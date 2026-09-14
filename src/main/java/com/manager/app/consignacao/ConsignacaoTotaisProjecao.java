package com.manager.app.consignacao;

import java.math.BigDecimal;

public interface ConsignacaoTotaisProjecao {
    Long getConsignacaoId();
    Long getItensCount();
    Integer getQuantidadeTotal();
    BigDecimal getValorTotal();
}
