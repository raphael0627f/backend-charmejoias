package com.manager.app.pedido;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import com.manager.app.grupo.Grupo;
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

    // quantidade desse item já cancelada (parcial ou total) - o item não é removido do pedido,
    // só deixa de contar como "a separar/entregar" por essa quantidade
    @Column(name = "quantidade_cancelada")
    private Integer quantidadeCancelada = 0;

    @Column(name = "motivo_cancelamento")
    private String motivoCancelamento;

    // "já separado" na lista de separação - só muda com o pedido em SEPARACAO
    private boolean separado;


    @ManyToOne
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_variacao_id")
    private ProdutoVariacao produtoVariacao;

    @ManyToOne
    @JoinColumn(name = "joia_id")
    private Joia joia;

    // só preenchido em itens avulsos (sem produto/joia cadastrado) - deixa o
    // relatório de separação agrupar esses itens por departamento também
    @ManyToOne
    @JoinColumn(name = "grupo_id")
    private Grupo grupo;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;
}
