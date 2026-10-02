package com.manager.app.pedido;

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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "pedidos")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Pedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String observacao;

    @Column(name = "data_entrega")
    private LocalDate dataEntrega;

    // data em que o pedido foi feito (informada pelo usuário) - diferente de createdAt, que é o
    // timestamp de auditoria de quando o registro foi criado no sistema
    @Column(name = "data_pedido")
    private LocalDate dataPedido;

    @Enumerated(EnumType.STRING)
    private StatusPedido status;

    @Column(name = "motivo_cancelamento")
    private String motivoCancelamento;

    @Enumerated(EnumType.STRING)
    private TipoItem tipo;

    @Enumerated(EnumType.STRING)
    private CategoriaPedido categoria;

    // marcado ao imprimir o relatório de separação/individual; volta pra false quando o pedido é editado
    private boolean impresso;

    @Column(name = "impresso_em")
    private LocalDateTime impressoEm;

    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PedidoProduto> produtos = new ArrayList<>();

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

    public void adicionarProduto(PedidoProduto produto) {
        produto.setPedido(this);
        this.produtos.add(produto);
    }

    public void removerProduto(PedidoProduto produto) {
        produto.setPedido(null);
        this.produtos.remove(produto);
    }
}
