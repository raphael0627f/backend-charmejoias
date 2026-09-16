package com.manager.app.consignacao;

import com.manager.app.mostruario.Mostruario;
import com.manager.app.shared.TipoItem;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.manager.app.cliente.Cliente;
import com.manager.app.usuario.Usuario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@EntityListeners(AuditingEntityListener.class)
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

    @Enumerated(EnumType.STRING)
    private TipoItem tipo;

    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "mostruario_id")
    private Mostruario mostruario;

    @OneToMany(mappedBy = "consignacao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConsignacaoProduto> produtos = new ArrayList<>();

    private LocalDate dataEntrega;

    private LocalDate dataPreAcertoConsignacao;

    private LocalDate dataAcertoConsignacao;

    private Boolean confirmada = false;

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

    public Integer getQuantidadeTotal() {
        return produtos.stream().mapToInt(ConsignacaoProduto::getQuantidade).sum();
    }

    public BigDecimal getValorTotal() {
        return produtos.stream().map(p -> p.getPreco().multiply(BigDecimal.valueOf(p.getQuantidade()))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void adicionarProduto(ConsignacaoProduto produto) {
        produto.setConsignacao(this);
        this.produtos.add(produto);
    }

    public void removerProduto(ConsignacaoProduto produto) {
        produto.setConsignacao(null);
        this.produtos.remove(produto);
    }
}
