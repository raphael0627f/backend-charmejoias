package com.manager.app.dto;

import com.manager.app.entity.Endereco;
import com.manager.app.entity.Grupo;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ClienteDTO {
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

    @NotBlank(message = "Gênero é obrigatório")
    private LocalDate dataNascimento;

    @NotBlank(message = "Gênero é obrigatório")
    private boolean ativo;

    private Endereco endereco;
    private List<Grupo> grupos;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
