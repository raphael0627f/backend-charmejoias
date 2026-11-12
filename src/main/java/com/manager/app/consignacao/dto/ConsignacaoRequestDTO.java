package com.manager.app.consignacao.dto;

import com.manager.app.shared.TipoItem;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ConsignacaoRequestDTO {

    private Long id;
    private UUID clienteId;
    private String descricao;

    @NotNull(message = "O campo Mostruário é obrigatório.")
    private Long mostruarioId;

    @NotNull(message = "O campo Tipo é obrigatório.")
    private TipoItem tipo;

    @NotNull(message = "Informe ao menos um produto na consignação.")
    private List<ConsignacaoProdutoRequestDTO> produtos;

    private LocalDate dataEntrega;
    private LocalDate dataPreAcertoConsignacao;
    private LocalDate dataAcertoConsignacao;

}
