package com.manager.app.rota;

import com.manager.app.consignacao.Consignacao;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rota_consignacoes_deixar")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RotaConsignacaoDeixar {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rota_cliente_id", nullable = false)
    private RotaCliente rotaCliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consignacao_id", nullable = false)
    private Consignacao consignacao;

    // preenchido só depois, quando o usuário edita a rota pra registrar se essa
    // consignação especifica realmente foi deixada em campo
    private Boolean confirmada = false;
}
