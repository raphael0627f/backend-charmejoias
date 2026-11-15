package com.manager.app.financeiro.dto;

import com.manager.app.financeiro.PeriodicidadeRecorrencia;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// corpos e respostas da regra de recorrência
public final class RecorrenciaDTOs {

    private RecorrenciaDTOs() {}

    public record Regra(Long id, PeriodicidadeRecorrencia periodicidade, LocalDate dataInicio, LocalDate dataFim, boolean ativa, boolean valorVariavel, String descricao,
                        String favorecido, ContaPagarResumoDTO.CategoriaRef categoria, ContaPagarResumoDTO.Ref centroCusto,
                        ContaPagarResumoDTO.Ref formaPrevista, BigDecimal valor, String documento, String observacao, LocalDateTime ultimaGeracao,
                        LocalDateTime encerradaEm, long quantidadeGerada, List<ContaPagarResumoDTO> proximas,
                        List<ContaPagarDetalheDTO.Evento> eventos, LocalDateTime createdAt, UsuarioResumoDTO createdBy,
                        LocalDateTime updatedAt, UsuarioResumoDTO updatedBy) {
    }

    // campos opcionais omitidos (null) mantêm o valor atual da regra
    public record Reajuste(@NotNull(message = "A data a partir da qual o reajuste vale é obrigatória.") LocalDate aPartirDe,
                           @NotNull(message = "O valor é obrigatório.") BigDecimal valor,
                           @Size(max = 200, message = "A descrição deve ter no máximo 200 caracteres.") String descricao,
                           @Size(max = 150, message = "O favorecido deve ter no máximo 150 caracteres.") String favorecido,
                           Long categoriaId, Long centroCustoId, Long formaPrevistaId,
                           @Size(max = 60, message = "O documento deve ter no máximo 60 caracteres.") String documento,
                           @Size(max = 1000, message = "A observação deve ter no máximo 1000 caracteres.") String observacao) {
    }

    public record ResultadoReajuste(int atualizadas, Regra regra) {
    }

    public record Encerramento(boolean cancelarFuturas, String motivo) {
    }

    public record ResultadoEncerramento(int canceladas, Regra regra) {
    }
}
