package com.manager.app.dto.produto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.entity.Categoria;
import com.manager.app.entity.Fornecedor;
import com.manager.app.entity.Grupo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "codigo",
        "referencia",
        "nome",
        "precoCusto",
        "precoVenda",
        "ativo",
        "observacoes",
        "categoria",
        "fornecedor",
        "grupos",
        "createdAt",
        "updatedAt"
})
public class ProdutoResponseDTO {

    private UUID id;
    private String codigo;
    private String referencia;
    private String nome;
    private BigDecimal precoCusto;
    private BigDecimal precoVenda;
    private boolean ativo;
    private String observacoes;
    private Categoria categoria;
    private Fornecedor fornecedor;
    private List<Grupo> grupos;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
