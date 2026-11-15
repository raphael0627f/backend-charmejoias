package com.manager.app.financeiro;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// regra de recorrência: modelo do título e periodicidade; as ocorrências são títulos comuns (conta_pagar.recorrencia_id)
@Entity
@Table(name = "conta_pagar_recorrencia")
@EqualsAndHashCode(of = "id", callSuper = false)
@Getter
@Setter
@NoArgsConstructor
public class ContaPagarRecorrencia extends AuditoriaFinanceira {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private PeriodicidadeRecorrencia periodicidade;

    private LocalDate dataInicio;
    private LocalDate dataFim;
    private String descricao;
    private String favorecido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id")
    private CategoriaDespesa categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "centro_custo_id")
    private CentroCusto centroCusto;

    private BigDecimal valor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forma_prevista_id")
    private FormaPagamento formaPrevista;

    private String documento;
    private String observacao;
    // contas de valor e vencimento diferentes a cada mês: as ocorrências nascem com estimado = true
    private boolean valorVariavel;
    private boolean ativa = true;
    private LocalDateTime ultimaGeracao;
    private LocalDateTime encerradaEm;
}
