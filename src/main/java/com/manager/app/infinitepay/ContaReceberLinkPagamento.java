package com.manager.app.infinitepay;

import com.manager.app.financeiro.ContaReceber;
import com.manager.app.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// link de pagamento gerado na InfinitePay para UM título a receber (parcela). Um título pode ter vários links.
@Entity
@Table(name = "conta_receber_link_pagamento")
@Getter
@Setter
@NoArgsConstructor
public class ContaReceberLinkPagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_receber_id")
    private ContaReceber contaReceber;

    @Column(name = "order_nsu")
    private String orderNsu;

    private String url;
    private BigDecimal valor;
    private String descricao;
    private LocalDate validade;

    @Column(name = "pagamento_unico")
    private boolean pagamentoUnico;

    @Column(name = "assured_installments")
    private int assuredInstallments;

    @Column(name = "pix_habilitado")
    private boolean pixHabilitado;

    @Column(name = "desconto_pix")
    private BigDecimal descontoPix = BigDecimal.ZERO;

    @Column(name = "desconto_cartao")
    private BigDecimal descontoCartao = BigDecimal.ZERO;

    @Column(name = "exige_endereco")
    private boolean exigeEndereco;

    @Column(name = "cliente_nome")
    private String clienteNome;

    @Column(name = "cliente_email")
    private String clienteEmail;

    @Column(name = "cliente_telefone")
    private String clienteTelefone;

    @Enumerated(EnumType.STRING)
    private StatusLinkPagamento status = StatusLinkPagamento.ATIVO;

    @Column(name = "recebimento_id")
    private Long recebimentoId;

    @Column(name = "resposta_bruta")
    private String respostaBruta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_id")
    private Usuario criadoPor;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;
}
