package com.manager.app.dto.cliente;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.entity.Endereco;
import com.manager.app.entity.Grupo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "codigo",
        "referencia",
        "nome",
        "filiacao",
        "cpf",
        "documento",
        "genero",
        "telefone",
        "celular",
        "email",
        "observacao",
        "dataNascimento",
        "ativo",
        "endereco",
        "grupos",
        "createdAt",
        "updatedAt"
})
public class ClienteResponseDTO {
    private UUID id;
    private String codigo;
    private String referencia;
    private String nome;
    private String filiacao;
    private String cpf;
    private String documento;
    private String genero;
    private String telefone;
    private String celular;
    private String email;
    private String observacao;
    private LocalDate dataNascimento;
    private boolean ativo;
    private Endereco endereco;
    private List<Grupo> grupos;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
