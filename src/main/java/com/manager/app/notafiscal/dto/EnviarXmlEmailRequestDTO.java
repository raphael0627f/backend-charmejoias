package com.manager.app.notafiscal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// limites de quantidade de notas e de destinatarios e formato dos e-mails sao conferidos no service (mensagens proprias)
@Getter
@Setter
@NoArgsConstructor
public class EnviarXmlEmailRequestDTO {

    @NotEmpty(message = "Selecione ao menos uma nota fiscal.")
    private List<@NotNull(message = "O número da nota fiscal é obrigatório.") Long> notaIds;

    @NotEmpty(message = "Informe ao menos um destinatário.")
    private List<String> para;

    private List<String> cc;

    @NotBlank(message = "O campo Assunto é obrigatório.")
    @Size(max = 200, message = "O assunto deve ter no máximo 200 caracteres.")
    private String assunto;

    @NotBlank(message = "O campo Mensagem é obrigatório.")
    @Size(max = 5000, message = "A mensagem deve ter no máximo 5000 caracteres.")
    private String corpo;
}
