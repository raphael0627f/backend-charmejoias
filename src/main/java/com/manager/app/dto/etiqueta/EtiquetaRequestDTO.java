package com.manager.app.dto.etiqueta;

import com.manager.app.infra.restEnum.Impressora;
import com.manager.app.infra.restEnum.TipoItem;
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
