package com.manager.app.montagem.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// instrução enviada no cadastro/edição da montagem; com id mantém a instrução existente (e se já foi feita)
@Getter
@Setter
@NoArgsConstructor
public class MontagemInstrucaoRequestDTO {

    private Long id;

    // instrução em branco é ignorada pelo serviço (não é erro)
    private String texto;
}
