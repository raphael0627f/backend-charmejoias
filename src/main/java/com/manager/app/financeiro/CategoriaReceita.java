package com.manager.app.financeiro;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// no máximo 2 níveis: grupo (sem pai) > subgrupo (pai = grupo)
@Entity
@Table(name = "categoria_receita")
@EqualsAndHashCode(of = "id", callSuper = false)
@Getter
@Setter
@NoArgsConstructor
public class CategoriaReceita extends AuditoriaFinanceira {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pai_id")
    private CategoriaReceita pai;

    private int ordem;
    private boolean ativo = true;
}
