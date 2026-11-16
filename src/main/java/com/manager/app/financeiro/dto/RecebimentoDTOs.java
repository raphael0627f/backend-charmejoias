package com.manager.app.financeiro.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// corpos e respostas de recebimento, estorno de recebimento e recebimento em lote
public final class RecebimentoDTOs {

    private RecebimentoDTOs() {}

    public record Request(
            @NotNull(message = "A data do recebimento é obrigatória.") LocalDate dataRecebimento,
            @NotNull(message = "O valor principal é obrigatório.") BigDecimal valorPrincipal,
            BigDecimal juros, BigDecimal multa, BigDecimal desconto,
            @NotNull(message = "O banco é obrigatório.") Long bancoId,
            @NotNull(message = "A forma de pagamento é obrigatória.") Long formaPagamentoId,
            @Size(max = 500, message = "A observação deve ter no máximo 500 caracteres.") String observacao,
            Long linkId) {
    }

    // aviso só existe quando o saldo do banco ficou negativo (possível apenas no estorno)
    public record Resultado(ContaReceberDetalheDTO.Recebimento recebimento, ContaReceberDetalheDTO titulo,
                            @JsonInclude(JsonInclude.Include.NON_NULL) String aviso) {
    }

    public record Lote(List<Long> ids, LocalDate dataRecebimento, Long bancoId, Long formaPagamentoId,
                       @Size(max = 500, message = "A observação deve ter no máximo 500 caracteres.") String observacao) {
    }

    public record ItemLote(Long id, Long numero, boolean recebido, Long recebimentoId, BigDecimal valorRecebido, BigDecimal valorAberto,
                           String situacao, String menssage) {
    }

    public record ResultadoLote(int recebidos, int recusados, BigDecimal totalRecebido, List<ItemLote> resultados) {
    }
}
