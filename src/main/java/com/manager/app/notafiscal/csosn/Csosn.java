package com.manager.app.notafiscal.csosn;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "csosn")
@EqualsAndHashCode(of = "codigo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Csosn {
    @Id
    private String codigo;

    private String descricao;
}
