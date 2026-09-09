package com.manager.app.consignacao;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import com.manager.app.joia.Joia;
import com.manager.app.produto.Produto;
import com.manager.app.produto.ProdutoVariacao;

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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consignacao_id")
    private Consignacao consignacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_variacao_id")
    private ProdutoVariacao produtoVariacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "joia_id")
    private Joia joia;

}