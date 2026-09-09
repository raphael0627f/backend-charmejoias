package com.manager.app.ncm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ncm")
@EqualsAndHashCode(of = "codigo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Ncm {
    @Id
    private String codigo;

    private String descricao;
    private boolean ativo;

    // percentual aproximado dos tributos (Lei 12.741/2012), preenchido a partir da tabela do IBPT
    @Column(precision = 5, scale = 2)
    private BigDecimal aliquotaAproximadaTributos;

    @JsonIgnore
    @CreationTimestamp
    private LocalDateTime createdAt;

    @JsonIgnore
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
