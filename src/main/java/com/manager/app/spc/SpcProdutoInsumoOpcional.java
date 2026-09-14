package com.manager.app.spc;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "spc_produto_insumos_opcionais")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SpcProdutoInsumoOpcional {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "produto_codigo")
    private Integer produtoCodigo;

    @Column(name = "insumo_codigo")
    private Integer insumoCodigo;

    @Column(name = "insumo_nome")
    private String insumoNome;
}
