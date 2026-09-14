package com.manager.app.spc;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "spc_motivos_exclusao")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SpcMotivoExclusao {
    @Id
    private Integer id;

    private String descricao;
}
