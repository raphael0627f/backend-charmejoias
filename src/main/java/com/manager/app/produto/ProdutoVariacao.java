package com.manager.app.produto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "produtos_variacoes")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoVariacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sku;

    @Column(precision = 10, scale = 2)
    private BigDecimal precoCusto;

    @Column(precision = 10, scale = 2)
    private BigDecimal precoVenda;

    private Integer quantidadeEstoque;

    private boolean ativo;

    private String codigoBarras;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @OneToMany(mappedBy = "produtoVariacao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProdutoVariacaoItem> itens = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public void adicionarItem(ProdutoVariacaoItem item) {
        item.setProdutoVariacao(this);
        this.itens.add(item);
    }
}
