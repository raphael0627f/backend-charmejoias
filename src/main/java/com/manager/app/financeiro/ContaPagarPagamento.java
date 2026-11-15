package com.manager.app.financeiro;

import com.manager.app.usuario.Usuario;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// baixa (pagamento) de um título; nesta fase só existe a entidade e a leitura (a fase 3 cria as baixas).
// valor_total = valor_principal + juros + multa - desconto
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "conta_pagar_baixa")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
public class ContaPagarBaixa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_pagar_id")
    private ContaPagar contaPagar;

    private LocalDate dataPagamento;
    private BigDecimal valorPrincipal;
    private BigDecimal juros = BigDecimal.ZERO;
    private BigDecimal multa = BigDecimal.ZERO;
    private BigDecimal desconto = BigDecimal.ZERO;
    private BigDecimal valorTotal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "banco_id")
    private Banco banco;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "forma_pagamento_id")
    private FormaPagamento formaPagamento;

    private String observacao;
    private boolean estornada;
    private String estornoMotivo;
    private LocalDateTime estornadaEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estornada_por_id")
    private Usuario estornadaPor;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private Usuario createdBy;
}
