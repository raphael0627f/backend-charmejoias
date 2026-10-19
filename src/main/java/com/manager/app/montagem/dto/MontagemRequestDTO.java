package com.manager.app.montagem.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
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

    private String instrucoes;

    @NotNull(message = "Data de entrega é obrigatória")
    private LocalDate dataEntrega;
}
