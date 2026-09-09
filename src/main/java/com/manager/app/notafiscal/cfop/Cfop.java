package com.manager.app.notafiscal.cfop;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "cfop")
@EqualsAndHashCode(of = "codigo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cfop {
    @Id
    private String codigo;

    private String descricao;
}
