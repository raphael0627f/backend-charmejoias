package com.manager.app.acertojoia;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.manager.app.usuario.Usuario;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "acertos_joia")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AcertoJoia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorConsignacoes;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorMaleta;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorAcessorios;

    @OneToMany(mappedBy = "acertoJoia", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AcertoJoiaConsignacao> consignacoes = new ArrayList<>();

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

    public BigDecimal valorPagarConsignacoesMaleta() {
        BigDecimal totalConsignacoes = BigDecimal.ZERO;

        for (AcertoJoiaConsignacao consignacao : consignacoes) {
            if (consignacao.getTipo() == TipoConsignacaoAcertoJoia.KIT || consignacao.getTipo() == TipoConsignacaoAcertoJoia.KIT_NOVO || consignacao.getTipo() == TipoConsignacaoAcertoJoia.MALETA) {
                if (consignacao.getValorTotal() != null) {
                    totalConsignacoes = totalConsignacoes.add(consignacao.getValorTotal());
                }
            }
        }

        BigDecimal totalBase = BigDecimal.ZERO;

        if (valorConsignacoes != null) {
            totalBase = totalBase.add(valorConsignacoes);
        }

        if (valorMaleta != null) {
            totalBase = totalBase.add(valorMaleta);
        }

        BigDecimal resultado = totalBase.subtract(totalConsignacoes);

        return resultado.multiply(new BigDecimal("0.3"));
    }

    public BigDecimal valorPagarAcessorios() {
        BigDecimal totalAcessorios = BigDecimal.ZERO;

        for (AcertoJoiaConsignacao acessorio : consignacoes) {
            if (acessorio.getTipo() == TipoConsignacaoAcertoJoia.ACESSORIO) {
                if (acessorio.getValorTotal() != null) {
                    totalAcessorios = totalAcessorios.add(acessorio.getValorTotal());
                }
            }
        }

        BigDecimal base = valorAcessorios != null ? valorAcessorios : BigDecimal.ZERO;
        BigDecimal resultado = base.subtract(totalAcessorios);

        return resultado.multiply(new BigDecimal("0.7"));
    }

    public void adicionarConsignacao(AcertoJoiaConsignacao consignacao) {
        consignacao.setAcertoJoia(this);
        this.consignacoes.add(consignacao);
    }

    public void removerConsignacao(AcertoJoiaConsignacao consignacao) {
        consignacao.setAcertoJoia(null);
        this.consignacoes.remove(consignacao);
    }
}
