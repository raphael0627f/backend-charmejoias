package com.manager.app.financeiro.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

// corpos das ações sobre o título: alterar vencimento, cancelar e cancelar em lote
public final class ContaPagarAcoesDTO {

    private ContaPagarAcoesDTO() {}

    public record Vencimento(@NotNull(message = "O novo vencimento é obrigatório.") LocalDate novoVencimento, String motivo) {
    }

    public record Cancelar(String motivo) {
    }

    public record CancelarLote(List<Long> ids, String motivo) {
    }

    public record ResultadoLote(int cancelados, int recusados, List<Item> resultados) {
    }

    public record Item(Long id, Long numero, boolean cancelado, String menssage) {
    }
}
