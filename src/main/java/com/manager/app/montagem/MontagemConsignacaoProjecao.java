package com.manager.app.montagem;

// vínculo consignação -> montagem, para consultas em lote (sem uma consulta por consignação)
public interface MontagemConsignacaoProjecao {
    Long getConsignacaoId();

    Long getMontagemId();
}
