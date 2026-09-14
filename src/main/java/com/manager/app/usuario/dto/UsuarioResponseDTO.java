package com.manager.app.usuario.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.endereco.Endereco;
import com.manager.app.usuario.Perfil;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "usuario",
        "primeiroNome",
        "sobrenome",
        "cpf",
        "documento",
        "celular",
        "email",
        "observacao",
        "endereco",
        "perfil",
        "ativo",
        "createdAt",
        "updatedAt"
})
public class UsuarioResponseDTO {
    // nunca inclui "senha" - de propósito, não é um esquecimento
    private UUID id;
    private String usuario;
    private String primeiroNome;
    private String sobrenome;
    private String cpf;
    private String documento;
    private String celular;
    private String email;
    private String observacao;
    private Endereco endereco;
    private Perfil perfil;
    private boolean ativo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
