package com.manager.app.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "consignacoes")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Consignacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descricao;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = true)
    private Cliente cliente;

    @OneToMany(mappedBy = "consignacao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConsignacaoProduto> produtos;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public void adicionarProduto(ConsignacaoProduto produto) {
        produto.setConsignacao(this);
        this.produtos.add(produto);
    }

    public void removerProduto(ConsignacaoProduto produto) {
        produto.setConsignacao(null);
        this.produtos.remove(produto);
    }
}
