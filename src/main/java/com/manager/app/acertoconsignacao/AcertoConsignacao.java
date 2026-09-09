package com.manager.app.acertoconsignacao;

import com.manager.app.consignacao.Consignacao;
import com.manager.app.consignacao.ConsignacaoProduto;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "acertos_consignacao")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AcertoConsignacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consignacao_id", nullable = false)
    private Consignacao consignacao;

    @Column(precision = 10, scale = 2)
    private BigDecimal desconto;

    private String observacoes;

    private Boolean confirmado = false;

    @OneToMany(mappedBy = "acertoConsignacao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AcertoConsignacaoProduto> produtos = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    AcertoConsignacaoProduto devolvidoDe(ConsignacaoProduto item){
        for (AcertoConsignacaoProduto devolvido : produtos) {
            Long variacaoItemId = item.getProdutoVariacao() != null ? item.getProdutoVariacao().getId() : null;
            Long variacaoDevolvidoId = devolvido.getProdutoVariacao() != null ? devolvido.getProdutoVariacao().getId() : null;
            boolean mesmoProduto = item.getProduto() != null && devolvido.getProduto() != null
                    && item.getProduto().getId().equals(devolvido.getProduto().getId())
                    && Objects.equals(variacaoItemId, variacaoDevolvidoId);
            boolean mesmaJoia = item.getJoia() != null && devolvido.getJoia() != null
                    && item.getJoia().getId().equals(devolvido.getJoia().getId());
            if (mesmoProduto || mesmaJoia) {
                return devolvido;
            }
        }
        return null;
    }

    public Integer getQuantidadeProdutosDevolvidos() {
        return produtos.stream().mapToInt(AcertoConsignacaoProduto::getQuantidade).sum();
    }

    public Integer getQuantidadeProdutosAcertados() {
        int total = 0;
        for (ConsignacaoProduto item : consignacao.getProdutos()) {
            AcertoConsignacaoProduto devolvido = devolvidoDe(item);
            int quantidadeDevolvida = devolvido != null ? devolvido.getQuantidade() : 0;
            total += item.getQuantidade() - quantidadeDevolvida;
        }
        return total;
    }

    public BigDecimal getValorTotalProdutosAcertados() {
        BigDecimal total = BigDecimal.ZERO;
        for (ConsignacaoProduto item : consignacao.getProdutos()) {
            AcertoConsignacaoProduto devolvido = devolvidoDe(item);
            int quantidadeDevolvida = devolvido != null ? devolvido.getQuantidade() : 0;
            int quantidadeAcertada = item.getQuantidade() - quantidadeDevolvida;
            total = total.add(item.getPreco().multiply(BigDecimal.valueOf(quantidadeAcertada)));
        }
        return total;
    }

    public BigDecimal getValorTotalComDesconto() {
        BigDecimal descontoAplicado = desconto != null ? desconto : BigDecimal.ZERO;
        return getValorTotalProdutosAcertados().subtract(descontoAplicado);
    }

    public void adicionarProduto(AcertoConsignacaoProduto produto) {
        produto.setAcertoConsignacao(this);
        this.produtos.add(produto);
    }

    public void removerProduto(AcertoConsignacaoProduto produto) {
        produto.setAcertoConsignacao(null);
        this.produtos.remove(produto);
    }
}
