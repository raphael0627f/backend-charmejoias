package com.manager.app.financeiro.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// corpos e respostas de pagamento, estorno de pagamento e pagamento em lote
public final class PagamentoDTOs {

    private PagamentoDTOs() {}

    public record Request(
            @NotNull(message = "A data do pagamento é obrigatória.") LocalDate dataPagamento,
            @NotNull(message = "O valor principal é obrigatório.") BigDecimal valorPrincipal,
            BigDecimal juros, BigDecimal multa, BigDecimal desconto,
            @NotNull(message = "O banco é obrigatório.") Long bancoId,
            @NotNull(message = "A forma de pagamento é obrigatória.") Long formaPagamentoId,
            @Size(max = 500, message = "A observação deve ter no máximo 500 caracteres.") String observacao,
            BigDecimal valorConta, LocalDate vencimento) {
    }

    // aviso só existe quando o saldo do banco ficou negativo
    public record Resultado(ContaPagarDetalheDTO.Pagamento pagamento, ContaPagarDetalheDTO titulo,
                            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String aviso) {
    }

    public record Lote(List<Long> ids, LocalDate dataPagamento, Long bancoId, Long formaPagamentoId,
                       @Size(max = 500, message = "A observação deve ter no máximo 500 caracteres.") String observacao) {
    }

    public record ItemLote(Long id, Long numero, boolean pago, Long pagamentoId, BigDecimal valorPago, BigDecimal valorAberto,
                           String situacao, String menssage) {
    }

    public record ResultadoLote(int pagos, int recusados, BigDecimal totalPago, List<ItemLote> resultados,
                                @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String aviso) {
    }
}
