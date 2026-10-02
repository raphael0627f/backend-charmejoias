package com.manager.app.preacerto;

import com.manager.app.cliente.Cliente;
import com.manager.app.consignacao.Consignacao;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.manager.app.usuario.Usuario;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@EntityListeners(AuditingEntityListener.class)
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

    // mesmo formato do desconto do Acerto: valor absoluto, nao percentual (ver
    // AcertoConsignacao.getValorTotalComDesconto)
    @Column(precision = 10, scale = 2)
    private BigDecimal desconto;

    @OneToMany(mappedBy = "preAcerto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PreAcertoConsignacaoProduto> produtos = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private Usuario createdBy;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private Usuario updatedBy;

    public void adicionarProduto(PreAcertoConsignacaoProduto produto) {
        produto.setPreAcerto(this);
        this.produtos.add(produto);
    }

    public void removerProduto(PreAcertoConsignacaoProduto produto) {
        produto.setPreAcerto(null);
        this.produtos.remove(produto);
    }
}
