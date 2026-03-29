package com.manager.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ProdutoDTO {


    private String id;

    @NotBlank(message = "Código é obrigatório")
    private String codigo;

    @NotBlank(message = "Referência é obrigatório")
    private String referencia;

    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    private BigDecimal precoCusto;

    @NotNull(message = "Preço Venda é obrigatório")
    private BigDecimal precoVenda;

    @NotNull(message = "Ativo é obrigatório")
    private boolean ativo;

    private String observacoes;

    @NotNull(message = "Categoria é obrigatória")
    private Long categoriaId;

    private Long fornecedorId;
    private List<Long> gruposIds;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
