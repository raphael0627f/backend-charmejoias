package com.manager.app.financeiro;

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

// título a pagar; nunca é excluído (cancelamento fica registrado). VENCIDO é calculado, não existe na coluna situacao.
@Entity
@Table(name = "conta_pagar")
@EqualsAndHashCode(of = "id", callSuper = false)
@Getter
@Setter
@NoArgsConstructor
public class ContaPagar extends AuditoriaFinanceira {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long numero;
    private String descricao;
    private String favorecido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id")
    private CategoriaDespesa categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "centro_custo_id")
    private CentroCusto centroCusto;

    private BigDecimal valor;
    private LocalDate emissao;

    // aaaa-mm
    private String competencia;
    private LocalDate vencimento;
    private String documento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forma_prevista_id")
    private FormaPagamento formaPrevista;

    private String linhaDigitavel;
    private String observacao;

    @Enumerated(EnumType.STRING)
    private SituacaoContaPagar situacao = SituacaoContaPagar.ABERTO;

    private BigDecimal valorPago = BigDecimal.ZERO;

    private UUID grupoParcelamentoId;
    private Integer parcelaNumero;
    private Integer parcelaTotal;

    private Long recorrenciaId;
    // data original da ocorrência na série (não muda ao remarcar o vencimento): base da idempotência da geração
    private LocalDate ocorrenciaData;

    // true = valor e vencimento ainda não confirmados (ocorrência de recorrência de valor variável); passa a false na confirmação
    private boolean estimado;

    private String cancelamentoMotivo;
    private LocalDateTime cancelamentoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelado_por_id")
    private Usuario canceladoPor;
}
