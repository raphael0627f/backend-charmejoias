package com.manager.app.montagem;

// total e quantos desses estão "feitos" por montagem (instruções marcadas, pedidos separados), para o andamento da listagem
public interface MontagemProgressoProjecao {
    Long getMontagemId();

    Long getTotal();

    Long getConcluidos();
}
