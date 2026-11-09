package com.manager.app.planejadorrota.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "Nome da rota é obrigatório")
    private String nome;

    @NotNull(message = "Data da rota é obrigatória")
    private LocalDate dataRota;

    @NotNull(message = "Esta rota do planejador precisa ter um ou mais Clientes")
    @Valid
    private List<PlanejadorRotaClienteRequestDTO> itens;
}
