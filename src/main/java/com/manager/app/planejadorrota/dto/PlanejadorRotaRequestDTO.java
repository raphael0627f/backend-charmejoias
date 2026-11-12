package com.manager.app.planejadorrota.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PlanejadorRotaRequestDTO {

    private Long id;

    @NotBlank(message = "O campo Nome da rota é obrigatório.")
    private String nome;

    @NotNull(message = "O campo Data da rota é obrigatório.")
    private LocalDate dataRota;

    @NotEmpty(message = "Informe ao menos um cliente na rota do planejador.")
    @Valid
    private List<PlanejadorRotaClienteRequestDTO> itens;
}
