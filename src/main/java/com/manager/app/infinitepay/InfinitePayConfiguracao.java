package com.manager.app.infinitepay;

import com.manager.app.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// configuração da InfinitePay: uma única linha (id = 1). A chave da API só existe criptografada e nunca sai pela API.
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "infinitepay_configuracao")
@Getter
@Setter
@NoArgsConstructor
public class InfinitePayConfiguracao {

    public static final Long ID_UNICO = 1L;

    @Id
    private Long id = ID_UNICO;

    private String handle;

    @Column(name = "api_key_criptografada")
    private String apiKeyCriptografada;

    @Column(name = "validade_dias")
    private int validadeDias = 7;

    @Column(name = "pagamento_unico")
    private boolean pagamentoUnico = true;

    @Column(name = "assured_installments")
    private int assuredInstallments = 0;

    @Column(name = "pix_habilitado")
    private boolean pixHabilitado = true;

    @Column(name = "desconto_pix")
    private BigDecimal descontoPix = BigDecimal.ZERO;

    @Column(name = "desconto_cartao")
    private BigDecimal descontoCartao = BigDecimal.ZERO;

    @Column(name = "exige_endereco")
    private boolean exigeEndereco = false;

    @Column(name = "texto_whatsapp")
    private String textoWhatsapp;

    @Column(name = "texto_email")
    private String textoEmail;

    // ---- baixa automatica (fase 3B): receptor na nuvem, interruptor e mapeamento banco/forma. Segredos criptografados e nunca devolvidos. ----
    @Column(name = "baixa_automatica_ativa")
    private boolean baixaAutomaticaAtiva = false;

    @Column(name = "modo_automatico_padrao")
    private boolean modoAutomaticoPadrao = false;

    @Column(name = "receptor_url")
    private String receptorUrl;

    @Column(name = "receptor_token_entrada_criptografado")
    private String receptorTokenEntradaCriptografado;

    @Column(name = "receptor_segredo_erp_criptografado")
    private String receptorSegredoErpCriptografado;

    @Column(name = "intervalo_fila_minutos")
    private int intervaloFilaMinutos = 2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "banco_padrao_id")
    private com.manager.app.financeiro.Banco bancoPadrao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forma_pix_id")
    private com.manager.app.financeiro.FormaPagamento formaPix;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forma_cartao_id")
    private com.manager.app.financeiro.FormaPagamento formaCartao;

    @UpdateTimestamp
    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atualizado_por_id")
    private Usuario atualizadoPor;
}
