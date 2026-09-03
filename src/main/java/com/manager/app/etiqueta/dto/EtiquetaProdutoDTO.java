package com.manager.app.etiqueta.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class EtiquetaProdutoDTO {

    private UUID produtoId;
    private Integer quantidade;
}
