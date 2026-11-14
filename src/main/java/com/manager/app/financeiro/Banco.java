package com.manager.app.financeiro;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

// conta financeira (banco, caixa, cartão, carteira digital); o saldo atual é a soma das movimentações (inclui o saldo inicial)
@Entity
@Table(name = "banco")
@EqualsAndHashCode(of = "id", callSuper = false)
@Getter
@Setter
@NoArgsConstructor
public class Banco extends AuditoriaFinanceira {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    @Enumerated(EnumType.STRING)
    private TipoBanco tipo;

    private String codigoBanco;
    private String agencia;
    private String conta;

    private BigDecimal saldoInicial = BigDecimal.ZERO;
    private LocalDate dataSaldoInicial;
    private String observacao;
    private boolean ativo = true;
}
