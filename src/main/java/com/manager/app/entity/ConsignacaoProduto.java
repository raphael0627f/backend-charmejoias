package com.manager.app.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "consignacoes_produtos")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConsignacaoProduto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer quantidade;

    @Column(precision = 10, scale = 2)
    private BigDecimal preco;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "consignacao_id")
    private Consignacao consignacao;

    @ManyToOne
    @JoinColumn(name = "produto_id")
    private Produto produto;

}