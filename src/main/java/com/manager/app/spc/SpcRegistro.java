package com.manager.app.spc;

import com.manager.app.cliente.Cliente;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.manager.app.usuario.Usuario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "spc_registros")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SpcRegistro {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacao")
    private TipoOperacaoSpc tipoOperacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pessoa")
    private TipoPessoaSpc tipoPessoa;

    private String numeroContrato;
    private LocalDate dataCompra;
    private LocalDate dataVencimento;

    @Enumerated(EnumType.STRING)
    private TipoDevedorSpc codigoTipoDevedor;

    private BigDecimal valorDebito;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "natureza_inclusao_id")
    private SpcNaturezaInclusao naturezaInclusao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motivo_exclusao_id")
    private SpcMotivoExclusao motivoExclusao;

    private String mensagemRetorno;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private Usuario createdBy;
}
