package com.manager.app.financeiro.dto;

import java.util.List;

// lançamento em lote de títulos simples
public final class LoteLancamentoDTOs {

    private LoteLancamentoDTOs() {}

    public record Request(List<ContaPagarRequestDTO> itens) {
    }

    public record Item(int indice, boolean criada, Long id, Long numero, String menssage, String campo) {
    }

    public record Resultado(int criadas, int recusadas, List<Item> resultados) {
    }
}
