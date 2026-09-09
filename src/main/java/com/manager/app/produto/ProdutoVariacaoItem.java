package com.manager.app.produto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.manager.app.variacao.ItemVariacao;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "produtos_variacoes_itens")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoVariacaoItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_variacao_id")
    private ProdutoVariacao produtoVariacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_variacao_id")
    private ItemVariacao itemVariacao;
}
