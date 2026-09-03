package com.manager.app.consignacao.dto;

import com.manager.app.shared.TipoItem;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ConsignacaoRequestDTO {

    private Long id;
    private UUID clienteId;
    private String descricao;

    @NotNull(message = "Tipo é obrigatório")
    private TipoItem tipo;

    @NotNull(message = "Esta Consignação precisa ter um ou mais Produtos")
    private List<ConsignacaoProdutoRequestDTO> produtos;

}
