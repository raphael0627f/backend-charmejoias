package com.manager.app.cliente.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.endereco.Endereco;
import com.manager.app.grupo.Grupo;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
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
        "enderecos",
        "grupos",
        "createdAt",
        "updatedAt",
        "createdBy",
        "updatedBy"
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
    private List<Endereco> enderecos;
    private List<Grupo> grupos;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UsuarioResumoDTO createdBy;
    private UsuarioResumoDTO updatedBy;
}
