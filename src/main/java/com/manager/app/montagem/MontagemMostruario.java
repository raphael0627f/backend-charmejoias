package com.manager.app.montagem;

import com.manager.app.cliente.Cliente;
import com.manager.app.consignacao.Consignacao;
import com.manager.app.mostruario.Mostruario;
import com.manager.app.pedido.Pedido;
import com.manager.app.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "montagens_mostruario")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MontagemMostruario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    // opcional: padrão para os pedidos/consignações criados pela própria montagem
    @ManyToOne
    @JoinColumn(name = "mostruario_id")
    private Mostruario mostruario;

    private String instrucoes;

    @Column(name = "data_entrega")
    private LocalDate dataEntrega;

    @Enumerated(EnumType.STRING)
    private StatusMontagem status;

    @Column(name = "motivo_cancelamento")
    private String motivoCancelamento;

    @Column(name = "iniciada_em")
    private LocalDateTime iniciadaEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "iniciada_por_id")
    private Usuario iniciadaPor;

    @Column(name = "concluida_em")
    private LocalDateTime concluidaEm;

    @ManyToMany
    @JoinTable(name = "montagens_mostruario_consignacoes",
            joinColumns = @JoinColumn(name = "montagem_id"),
            inverseJoinColumns = @JoinColumn(name = "consignacao_id"))
    @OrderBy("id")
    private List<Consignacao> consignacoes = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "montagens_mostruario_pedidos",
            joinColumns = @JoinColumn(name = "montagem_id"),
            inverseJoinColumns = @JoinColumn(name = "pedido_id"))
    @OrderBy("id")
    private List<Pedido> pedidos = new ArrayList<>();

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
}
