package com.manager.app.acertoconsignacao;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import com.manager.app.joia.Joia;
import com.manager.app.produto.Produto;
import com.manager.app.produto.ProdutoVariacao;

@Entity
@Table(name = "acertos_consignacao_produtos")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AcertoConsignacaoProduto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer quantidade;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acerto_consignacao_id")
    private AcertoConsignacao acertoConsignacao;

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
