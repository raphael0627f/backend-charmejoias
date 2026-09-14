package com.manager.app.spc;

import com.manager.app.cliente.Cliente;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "spc_consultas")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SpcConsulta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pessoa")
    private TipoPessoaSpc tipoPessoa;

    private Integer codigoProduto;
    private Long protocoloNumero;
    private Integer protocoloDigito;
    private String insumosOpcionaisSolicitados;
    private boolean possuiRestricao;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
