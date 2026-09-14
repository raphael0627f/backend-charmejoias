package com.manager.app.preacerto;

import java.math.BigDecimal;

public interface PreAcertoConsignacaoTotaisProjecao {
    Long getPreAcertoId();
    Long getItensCount();
    Integer getQuantidadeTotal();
    BigDecimal getValorTotal();
}
