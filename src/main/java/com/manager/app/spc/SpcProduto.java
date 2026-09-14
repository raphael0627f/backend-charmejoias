package com.manager.app.spc;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "spc_produtos")
@EqualsAndHashCode(of = "codigo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SpcProduto {
    @Id
    private Integer codigo;

    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pessoa")
    private TipoPessoaProdutoSpc tipoPessoa;

    public boolean aceita(TipoPessoaSpc tipoPessoaConsulta){
        return tipoPessoa == TipoPessoaProdutoSpc.AMBOS || tipoPessoa.name().equals(tipoPessoaConsulta.name());
    }
}
