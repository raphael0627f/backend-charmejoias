package com.manager.app.financeiro.dto;

import com.manager.app.financeiro.IntervaloParcelamento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// criação (simples ou parcelada, com o bloco parcelamento) e edição de título; no PUT o bloco parcelamento é ignorado
public record ContaPagarRequestDTO(
        @NotBlank(message = "A descrição é obrigatória.") @Size(max = 200, message = "A descrição deve ter no máximo 200 caracteres.") String descricao,
        @Size(max = 150, message = "O favorecido deve ter no máximo 150 caracteres.") String favorecido,
        @NotNull(message = "A categoria é obrigatória.") Long categoriaId,
        Long centroCustoId,
        @NotNull(message = "O valor é obrigatório.") BigDecimal valor,
        LocalDate emissao,
        String competencia,
        LocalDate vencimento,
        @Size(max = 60, message = "O documento deve ter no máximo 60 caracteres.") String documento,
        Long formaPrevistaId,
        @Size(max = 80, message = "A linha digitável excede o tamanho máximo.") String linhaDigitavel,
        @Size(max = 1000, message = "A observação deve ter no máximo 1000 caracteres.") String observacao,
        Parcelamento parcelamento,
        Recorrencia recorrencia,
        Boolean valorVariavel,
        Boolean confirmarDados,
        UUID clienteId) {

    // clienteId: cliente como favorecido, somente em lançamento individual (simples ou parcelado); ausente = favorecido avulso (texto). No PUT, ausente torna o título avulso.
    // valorVariavel (raiz ou no bloco recorrencia): ocorrências estimadas; confirmarDados: só no PUT, confirma valor/vencimento de título estimado
    // exclusivo com parcelamento; o vencimento informado é a primeira ocorrência
    public record Recorrencia(com.manager.app.financeiro.PeriodicidadeRecorrencia periodicidade, LocalDate dataFim, Boolean valorVariavel) {
    }


    public record Parcelamento(Integer quantidade, LocalDate primeiroVencimento, IntervaloParcelamento intervalo,
                               List<LocalDate> datas, List<BigDecimal> valores) {
    }
}
