package com.manager.app.usuario.dto;

import com.manager.app.endereco.EnderecoDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UsuarioRequestDTO {

    private String id;

    @NotBlank(message = "Usuário é obrigatório")
    private String usuario;

    // obrigatório no create; no update, em branco/nulo significa "não trocar a senha" (tratado no
    // service, não dá pra validar isso via anotação porque a mesma DTO serve pros dois casos)
    private String senha;

    private String primeiroNome;
    private String sobrenome;
    private String cpf;
    private String documento;
    private String celular;
    private String email;
    private String observacao;

    @Valid
    private EnderecoDTO endereco;

    private Long perfilId;
    private boolean ativo;
}
