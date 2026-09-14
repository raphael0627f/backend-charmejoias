package com.manager.app.spc;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "spc_naturezas_inclusao")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SpcNaturezaInclusao {
    @Id
    private Integer id;

    private String nome;
}
