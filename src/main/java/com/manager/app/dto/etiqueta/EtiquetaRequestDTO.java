package com.manager.app.dto.etiqueta;

import com.manager.app.infra.restEnum.Impressora;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class EtiquetaRequestDTO {

    private Integer inicio;
    private Impressora impressora;
    private List<EtiquetaProdutoDTO> produtos;
}
