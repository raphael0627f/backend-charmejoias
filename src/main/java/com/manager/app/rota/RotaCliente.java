package com.manager.app.rota;

import com.manager.app.cliente.Cliente;
import com.manager.app.consignacao.Consignacao;
import com.manager.app.endereco.Endereco;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rota_clientes")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RotaCliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rota_id", nullable = false)
    private Rota rota;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    // endereço escolhido pelo usuário entre os cadastrados do cliente, usado pra
    // latitude/longitude - a coluna "Endereço" do xlsx exportado traz todos os
    // endereços do cliente, começando por este
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endereco_id", nullable = false)
    private Endereco endereco;

    private Integer ordem;

    private String observacao;

    // o cliente pode ter no máximo uma consignação a deixar e uma a trazer por rota - nunca as duas nulas
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consignacao_deixar_id")
    private Consignacao consignacaoDeixar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consignacao_trazer_id")
    private Consignacao consignacaoTrazer;

    // preenchidos só depois, quando o usuário edita a rota pra registrar o que
    // realmente aconteceu em campo - nulos enquanto a rota está só planejada
    private Boolean deixouReal;
    private Boolean trouxeReal;
}
