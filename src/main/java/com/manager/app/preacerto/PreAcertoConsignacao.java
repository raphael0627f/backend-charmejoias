package com.manager.app.preacerto;

import com.manager.app.cliente.Cliente;
import com.manager.app.consignacao.Consignacao;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pre_acertos_consignacao")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PreAcertoConsignacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consignacao_id")
    private Consignacao consignacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    private String observacoes;

    @OneToMany(mappedBy = "preAcerto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PreAcertoConsignacaoProduto> produtos = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public void adicionarProduto(PreAcertoConsignacaoProduto produto) {
        produto.setPreAcerto(this);
        this.produtos.add(produto);
    }

    public void removerProduto(PreAcertoConsignacaoProduto produto) {
        produto.setPreAcerto(null);
        this.produtos.remove(produto);
    }
}
