package com.manager.app.montagem;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// quanto de um item de pedido já foi incluído (somado) em uma consignação da montagem.
// Os produtos dos pedidos são somados à consignação (consignação com 3 + pedido com 4 = 7); este registro é o que
// permite saber, depois, o que já foi incluído e o que ainda falta
@Entity
@Table(name = "montagens_mostruario_itens_incluidos")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
public class MontagemItemIncluido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "montagem_id")
    private Long montagemId;

    @Column(name = "consignacao_id")
    private Long consignacaoId;

    @Column(name = "pedido_produto_id")
    private Long pedidoProdutoId;

    private Integer quantidade;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
