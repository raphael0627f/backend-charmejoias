package com.manager.app.entity;

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

    public void adicionarConsignacao(AcertoConsignacao consignacao) {
        consignacao.setAcerto(this);
        this.consignacoes.add(consignacao);
    }

    public void removerConsignacao(AcertoConsignacao consignacao) {
        consignacao.setAcerto(null);
        this.consignacoes.remove(consignacao);
    }
}
