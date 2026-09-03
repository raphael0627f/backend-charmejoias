package com.manager.app.acerto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import com.manager.app.consignacao.Consignacao;

import java.math.BigDecimal;

@Entity
@Table(name = "acertos_consignacoes")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AcertoConsignacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descricao;
    private Integer quantidadeProdutos;

    @Enumerated(EnumType.STRING)
    private TipoConsignacaoAcerto tipo;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consignacao_id")
    private Consignacao consignacao;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acerto_id")
    private Acerto acerto;

}
