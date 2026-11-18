package com.manager.app.financeiro.dto;

import com.manager.app.usuario.dto.UsuarioResumoDTO;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class CreditoClienteDTOs {

    private CreditoClienteDTOs() {}

    public record DestinarRequest(@NotBlank(message = "Informe o destino do excedente (CREDITO).") String destino) {
    }

    public record MotivoRequest(@NotBlank(message = "Informe o motivo.") @Size(min = 5, max = 500, message = "O motivo deve ter entre 5 e 500 caracteres.") String motivo) {
    }

    public record LancamentoRequest(@NotNull(message = "Informe o valor do crédito.") @DecimalMin(value = "0.01", message = "O valor do crédito deve ser maior que zero.") BigDecimal valor,
                                    @NotBlank(message = "Informe o motivo.") @Size(min = 5, max = 500, message = "O motivo deve ter entre 5 e 500 caracteres.") String motivo) {
    }

    public record Lote(Long id, String origemTipo, Long origemId, BigDecimal valorOriginal, BigDecimal valorUtilizado, BigDecimal disponivel, String situacao,
                       String motivo, LocalDateTime criadoEm, UsuarioResumoDTO criadoPor) {
    }

    public record Credito(BigDecimal saldo, List<Lote> lotes) {
    }

    public record Movimento(Long id, String tipo, BigDecimal valor, Long loteId, Long contaReceberId, Long recebimentoId, String motivo, UsuarioResumoDTO usuario,
                            LocalDateTime dataHora, BigDecimal saldoApos) {
    }

    public record Extrato(BigDecimal saldoAtual, List<Movimento> content, int page, int size, long totalElements, int totalPages) {
    }
}
