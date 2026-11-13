package com.manager.app.planejadorrota;

import com.manager.app.pedido.Pedido;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "planejador_rota_pedidos_deixar")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlanejadorRotaPedidoDeixar {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planejador_rota_cliente_id", nullable = false)
    private PlanejadorRotaCliente rotaCliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    // preenchido só depois, quando o usuário edita a rota pra registrar se esse
    // pedido especifico realmente foi deixado em campo
    private Boolean confirmada = false;
}
