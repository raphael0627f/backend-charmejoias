package com.manager.app.joia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.manager.app.categoria.Categoria;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class JoiaRequestDTO {

    private String id;

    @NotBlank(message = "O campo Código é obrigatório.")
    private String codigo;

    @NotBlank(message = "O campo Referência é obrigatório.")
    private String referencia;

    @NotBlank(message = "O campo Nome é obrigatório.")
    private String nome;

    @NotNull(message = "O campo Preço de venda é obrigatório.")
    private BigDecimal precoVenda;

    @NotNull(message = "O campo Ativo é obrigatório.")
    private boolean ativo;

    private String observacoes;

    @NotNull(message = "O campo Categoria é obrigatório.")
    private Long categoriaId;

    private List<Long> gruposIds;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
