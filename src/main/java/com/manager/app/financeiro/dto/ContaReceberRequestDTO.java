package com.manager.app.financeiro.dto;

import com.manager.app.financeiro.IntervaloParcelamento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// informar clienteId OU pagadorNome (pagador avulso, com pagadorDocumento opcional); criação (simples ou parcelada, com o bloco parcelamento) e edição de título a receber; no PUT o bloco parcelamento é ignorado
public record ContaReceberRequestDTO(
        @NotBlank(message = "A descrição é obrigatória.") @Size(max = 200, message = "A descrição deve ter no máximo 200 caracteres.") String descricao,
        UUID clienteId,
        String pagadorNome,
        String pagadorDocumento,
        @NotNull(message = "A categoria é obrigatória.") Long categoriaId,
        Long centroCustoId,
        @NotNull(message = "O valor é obrigatório.") BigDecimal valor,
        LocalDate emissao,
        String competencia,
        LocalDate vencimento,
        @Size(max = 60, message = "O documento deve ter no máximo 60 caracteres.") String documento,
        Long formaPrevistaId,
        @Size(max = 1000, message = "A observação deve ter no máximo 1000 caracteres.") String observacao,
        Parcelamento parcelamento) {

    public record Parcelamento(Integer quantidade, LocalDate primeiroVencimento, IntervaloParcelamento intervalo,
                               List<LocalDate> datas, List<BigDecimal> valores) {
    }
}
