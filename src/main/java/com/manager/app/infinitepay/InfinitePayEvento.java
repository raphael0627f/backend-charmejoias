package com.manager.app.infinitepay;

import com.manager.app.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// evento (webhook) recebido do receptor, com o payload bruto para diagnóstico. Único por (transaction_nsu, evento): garante a idempotência.
// REVISAO = precisa de decisão do usuário (nunca baixa sozinho). Usado a partir da fase 3 (processamento) e 4 (revisão).
@Entity
@Table(name = "infinitepay_evento")
@Getter
@Setter
@NoArgsConstructor
public class InfinitePayEvento {

    public enum Status { AGUARDANDO, RECEBIDO, PROCESSADO, REVISAO, IGNORADO, ERRO_TEMPORARIO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_nsu")
    private String transactionNsu;

    private String evento;

    @Column(name = "order_nsu")
    private String orderNsu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link_id")
    private ContaReceberLinkPagamento link;

    @Enumerated(EnumType.STRING)
    private Status status = Status.RECEBIDO;

    private String motivo;
    private String payload;

    @Column(name = "amount_centavos")
    private Long amountCentavos;

    @Column(name = "paid_amount_centavos")
    private Long paidAmountCentavos;

    @Column(name = "capture_method")
    private String captureMethod;

    @Column(name = "recebimento_id")
    private Long recebimentoId;

    private int tentativas;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "processado_em")
    private LocalDateTime processadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolvido_por_id")
    private Usuario resolvidoPor;

    @Column(name = "resolvido_em")
    private LocalDateTime resolvidoEm;

    private String resolucao;
}
