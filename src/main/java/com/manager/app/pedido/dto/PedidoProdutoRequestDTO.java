package com.manager.app.pedido.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PedidoProdutoRequestDTO {

    // id do item já salvo (quando vem, o item é atualizado em vez de procurado por produto/joia) -
    // necessário pra itens avulsos, que não têm produto/joia pra casar
    private Long id;

    private String descricao;
    private String variacao;
    @NotNull(message = "Quantidade é obrigatória")
    @Positive(message = "Quantidade deve ser maior que zero")
    private Integer quantidade;

    @NotNull(message = "Preço é obrigatório")
    @PositiveOrZero(message = "Preço não pode ser negativo")
    private BigDecimal preco;
    private UUID produtoId;
    private Long produtoVariacaoId;
    private UUID joiaId;
    // só usado em itens avulsos (sem produtoId/joiaId) - departamento pra separação
    private Long grupoId;
}
