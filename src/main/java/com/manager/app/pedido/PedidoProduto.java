package com.manager.app.pedido;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import com.manager.app.joia.Joia;
import com.manager.app.produto.Produto;
import com.manager.app.produto.ProdutoVariacao;

import java.math.BigDecimal;

@Entity
@Table(name = "pedidos_produtos")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PedidoProduto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descricao;
    private String variacao;
    private Integer quantidade;

    @Column(precision = 10, scale = 2)
    private BigDecimal preco;


    @ManyToOne
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_variacao_id")
    private ProdutoVariacao produtoVariacao;

    @ManyToOne
    @JoinColumn(name = "joia_id")
    private Joia joia;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;
}
