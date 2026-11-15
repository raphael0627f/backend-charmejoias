package com.manager.app.financeiro.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// corpos das ações sobre o título a receber: alterar vencimento, cancelar, cancelar em lote e busca de clientes
public final class ContaReceberAcoesDTO {

    private ContaReceberAcoesDTO() {}

    public record Vencimento(@NotNull(message = "O novo vencimento é obrigatório.") @JsonAlias("novoVencimento") LocalDate dataVencimento, String motivo) {
    }

    public record Cancelar(String motivo) {
    }

    public record CancelarLote(List<Long> ids, String motivo) {
    }

    public record ResultadoLote(int cancelados, int recusados, List<Item> resultados) {
    }

    public record Item(Long id, Long numero, boolean cancelado, String menssage) {
    }

    public record Cliente(UUID id, String nome, String documento, String cidade) {
    }
}
