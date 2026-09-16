package com.manager.app.usuario.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// versao enxuta do Usuario pra usar nos campos createdBy/updatedBy de qualquer outro
// DTO de resposta, sem trazer perfil/endereco/etc
@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({ "id", "usuario", "primeiroNome", "sobrenome" })
public class UsuarioResumoDTO {
    private UUID id;
    private String usuario;
    private String primeiroNome;
    private String sobrenome;
}
