package com.manager.app.entity;

import com.manager.app.infra.restEnum.TipoConsignacaoAcerto;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "acertos")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Acerto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorConsignacoes;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorMaleta;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorAcessorios;

    @OneToMany(mappedBy = "acerto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AcertoConsignacao> consignacoes = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public BigDecimal valorPagarConsignacoesMaleta() {
        BigDecimal totalConsignacoes = BigDecimal.ZERO;

        for (AcertoConsignacao consignacao : consignacoes) {
            if (consignacao.getTipo() == TipoConsignacaoAcerto.KIT || consignacao.getTipo() == TipoConsignacaoAcerto.KIT_NOVO || consignacao.getTipo() == TipoConsignacaoAcerto.MALETA) {
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

        for (AcertoConsignacao acessorio : consignacoes) {
            if (acessorio.getTipo() == TipoConsignacaoAcerto.ACESSORIO) {
                if (acessorio.getValorTotal() != null) {
                    totalAcessorios = totalAcessorios.add(acessorio.getValorTotal());
                }
            }
        }

        BigDecimal base = valorAcessorios != null ? valorAcessorios : BigDecimal.ZERO;
        BigDecimal resultado = base.subtract(totalAcessorios);

        return resultado.multiply(new BigDecimal("0.7"));
    }

    public void adicionarConsignacao(AcertoConsignacao consignacao) {
        consignacao.setAcerto(this);
        this.consignacoes.add(consignacao);
    }

    public void removerConsignacao(AcertoConsignacao consignacao) {
        consignacao.setAcerto(null);
        this.consignacoes.remove(consignacao);
    }
}
