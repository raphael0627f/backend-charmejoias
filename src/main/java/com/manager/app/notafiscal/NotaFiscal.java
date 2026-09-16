package com.manager.app.notafiscal;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.manager.app.cliente.Cliente;
import com.manager.app.empresa.Empresa;
import com.manager.app.transportadora.Transportadora;
import com.manager.app.usuario.Usuario;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "notas_fiscais")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotaFiscal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    private Integer serie;
    private Long numero;

    private String naturezaOperacao;
    private boolean consumidorFinal;
    private Integer indicadorPresenca;

    @Enumerated(EnumType.STRING)
    private AmbienteNfe ambiente;

    @Enumerated(EnumType.STRING)
    private StatusNotaFiscal status;

    private String chaveAcesso;
    private String protocoloAutorizacao;
    private String motivoRejeicao;
    private String motivoRejeicaoCancelamento;
    private String contoraDocumentId;

    // conta quantas vezes essa nota foi enviada pra Contora (emitir()). Usado pra compor a
    // Idempotency-Key de cada tentativa: sem isso, reenviar uma nota REJEITADA depois de corrigida
    // reusaria a mesma chave da tentativa anterior e a Contora devolveria a resposta antiga
    // (rejeitada) em vez de processar o payload corrigido.
    @Column(nullable = false)
    private Integer numeroTentativaEnvio = 0;

    private LocalDateTime dataSaidaEntrada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transportadora_id")
    private Transportadora transportadora;

    private Integer modalidadeFrete;

    @Column(columnDefinition = "TEXT")
    private String informacoesComplementares;

    // desconto único informado pelo usuário pra nota inteira; guardado só pra referência/exibição,
    // já foi rateado entre os itens (NotaFiscalItem.desconto) no momento da criação/atualização
    @Column(precision = 10, scale = 2)
    private BigDecimal descontoTotal;

    @OneToMany(mappedBy = "notaFiscal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NotaFiscalItem> itens = new ArrayList<>();

    @OneToMany(mappedBy = "notaFiscal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NotaFiscalPagamento> pagamentos = new ArrayList<>();

    @OneToMany(mappedBy = "notaFiscal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NotaFiscalCorrecao> correcoes = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private Usuario createdBy;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private Usuario updatedBy;

    public void adicionarItem(NotaFiscalItem item) {
        item.setNotaFiscal(this);
        this.itens.add(item);
    }

    public void removerItem(NotaFiscalItem item) {
        item.setNotaFiscal(null);
        this.itens.remove(item);
    }

    public void adicionarPagamento(NotaFiscalPagamento pagamento) {
        pagamento.setNotaFiscal(this);
        this.pagamentos.add(pagamento);
    }

    public void removerPagamento(NotaFiscalPagamento pagamento) {
        pagamento.setNotaFiscal(null);
        this.pagamentos.remove(pagamento);
    }

    public void adicionarCorrecao(NotaFiscalCorrecao correcao) {
        correcao.setNotaFiscal(this);
        this.correcoes.add(correcao);
    }
}
