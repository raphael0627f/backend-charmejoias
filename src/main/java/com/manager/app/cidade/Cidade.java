package com.manager.app.cidade;

import jakarta.persistence.*;
import lombok.*;

// catálogo IBGE (municípios e distritos), somente leitura: populado por migration
@Entity
@Table(name = "cidades")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cidade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    private String uf;

    private String ibge;

    @Enumerated(EnumType.STRING)
    private CidadeTipo tipo;

    // só distrito: nome do município pai
    private String municipioNome;

    // nome (+ município, no distrito) em minúsculo e sem acento, para a busca
    private String nomeBusca;

    // só o nome, minúsculo e sem acento (ordenação por relevância da busca)
    private String nomeNormalizado;
}
