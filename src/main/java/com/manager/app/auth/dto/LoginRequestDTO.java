package com.manager.app.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LoginRequestDTO {

    @NotBlank(message = "O campo Usuário é obrigatório.")
    private String usuario;

    @NotBlank(message = "O campo Senha é obrigatório.")
    private String senha;
}
