package com.manager.app.cliente.dto;

import com.manager.app.endereco.EnderecoDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ClienteRequestDTO {
    private String id;

    @NotBlank(message = "Código é obrigatório")
    private String codigo;

    private String referencia;

    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    private String filiacao;
    private String cpf;
    private String documento;

    @NotBlank(message = "Gênero é obrigatório")
    private String genero;

    private String telefone;
    private String celular;
    private String email;
    private String observacao;

    @NotNull(message = "Data de nascimento é obrigatória")
    private LocalDate dataNascimento;

    @NotNull(message = "Ativo é obrigatório")
    private boolean ativo;

    private EnderecoDTO endereco;
    private List<Long> gruposIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
