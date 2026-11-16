package com.manager.app.financeiro;

import com.manager.app.cliente.Cliente;
import com.manager.app.usuario.Usuario;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// título a receber; nunca é excluído (cancelamento fica registrado). VENCIDO é calculado, não existe na coluna situacao.
@Entity
@Table(name = "conta_receber")
@EqualsAndHashCode(of = "id", callSuper = false)
@Getter
@Setter
@NoArgsConstructor
public class ContaReceber extends AuditoriaFinanceira {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long numero;

    // nulo quando o título é de pagador avulso (pagadorNome preenchido)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    private String pagadorNome;
    private String pagadorDocumento;

    // origem (V72): título gerado a partir do pré-acerto OU do acerto de consignação; nulos nos demais títulos
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consignacao_id")
    private com.manager.app.consignacao.Consignacao consignacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pre_acerto_id")
    private com.manager.app.preacerto.PreAcertoConsignacao preAcerto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acerto_id")
    private com.manager.app.acertoconsignacao.AcertoConsignacao acerto;

    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id")
    private CategoriaReceita categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "centro_custo_id")
    private CentroCusto centroCusto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forma_prevista_id")
    private FormaPagamento formaPrevista;

    private BigDecimal valor;
    private BigDecimal valorRecebido = BigDecimal.ZERO;
    private LocalDate emissao;
    private LocalDate vencimento;

    // aaaa-mm
    private String competencia;
    private String documento;
    private String observacao;

    @Enumerated(EnumType.STRING)
    private SituacaoContaReceber situacao = SituacaoContaReceber.ABERTO;

    private UUID grupoParcelamentoId;
    private Integer parcelaNumero;
    private Integer parcelaTotal;

    private String cancelamentoMotivo;
    private LocalDateTime cancelamentoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelado_por_id")
    private Usuario canceladoPor;
}
