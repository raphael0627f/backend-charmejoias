package com.manager.app.dto;

import com.manager.app.entity.Cliente;
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

    @NotNull(message = "Esta Consignação precisa ter um ou mais Produtos")
    private List<ConsignacaoProdutoRequestDTO> produtos;

}
