package com.manager.app.entity;

import com.manager.app.infra.restEnum.AmbienteNfe;
import com.manager.app.infra.restEnum.StatusNotaFiscal;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
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
    private String contoraDocumentId;

    @OneToMany(mappedBy = "notaFiscal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NotaFiscalItem> itens = new ArrayList<>();

    @OneToMany(mappedBy = "notaFiscal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NotaFiscalPagamento> pagamentos = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

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
}
