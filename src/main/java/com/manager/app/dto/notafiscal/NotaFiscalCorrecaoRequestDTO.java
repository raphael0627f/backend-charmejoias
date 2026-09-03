package com.manager.app.dto.notafiscal;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NotaFiscalCorrecaoRequestDTO {

    @NotBlank(message = "Texto da carta de correção é obrigatório")
    private String texto;
}
