package com.manager.app.montagem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class MontagemRequestDTO {

    private Long id;

    @NotNull(message = "clienteId é obrigatório")
    private UUID clienteId;

    // opcional
    private Long mostruarioId;

    // lista de instruções; as que não vierem na edição são removidas
    @Valid
    private List<MontagemInstrucaoRequestDTO> instrucoes;

    @NotNull(message = "Data de entrega é obrigatória")
    private LocalDate dataEntrega;
}
