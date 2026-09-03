package com.manager.app.etiqueta.dto;

import com.manager.app.etiqueta.Impressora;
import com.manager.app.shared.TipoItem;
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
    private TipoItem tipo;
    private List<EtiquetaProdutoDTO> produtos;
}
