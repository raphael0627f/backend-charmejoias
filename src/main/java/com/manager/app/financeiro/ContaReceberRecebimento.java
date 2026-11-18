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

// recebimento de um título a receber. valor_total = valor_principal + juros + multa - desconto (entra no banco); só o principal abate o título
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "conta_receber_recebimento")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
public class ContaReceberRecebimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_receber_id")
    private ContaReceber contaReceber;

    private LocalDate dataRecebimento;
    private BigDecimal valorPrincipal;
    private BigDecimal juros = BigDecimal.ZERO;
    private BigDecimal multa = BigDecimal.ZERO;
    private BigDecimal desconto = BigDecimal.ZERO;
    private BigDecimal valorTotal;

    // BANCO: entra no caixa (banco e forma obrigatórios, gera movimentação). CREDITO_CLIENTE: aplicação de crédito, sem banco, sem forma e fora do caixa.
    public enum Origem { BANCO, CREDITO_CLIENTE }

    @Enumerated(EnumType.STRING)
    private Origem origem = Origem.BANCO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "banco_id")
    private Banco banco;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forma_pagamento_id")
    private FormaPagamento formaPagamento;

    public boolean isCredito() {
        return origem == Origem.CREDITO_CLIENTE;
    }

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
