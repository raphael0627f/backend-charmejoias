package com.manager.app.usuario.dto;

import com.manager.app.usuario.Permissao;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class PerfilRequestDTO {

    private Long id;

    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    private Set<Permissao> permissoes;
}
