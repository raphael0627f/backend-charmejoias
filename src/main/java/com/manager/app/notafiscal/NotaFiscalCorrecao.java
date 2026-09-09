package com.manager.app.notafiscal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notas_fiscais_correcoes")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotaFiscalCorrecao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer sequencia;

    @Column(columnDefinition = "TEXT")
    private String texto;

    @Enumerated(EnumType.STRING)
    private StatusCorrecao status;

    private String protocolo;
    private String motivoRejeicao;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nota_fiscal_id")
    private NotaFiscal notaFiscal;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
