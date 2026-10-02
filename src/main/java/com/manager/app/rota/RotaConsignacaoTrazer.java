package com.manager.app.rota;

import com.manager.app.consignacao.Consignacao;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rota_consignacoes_trazer")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RotaConsignacaoTrazer {
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
    // consignação especifica realmente foi trazida de campo
    private Boolean confirmada = false;
}
