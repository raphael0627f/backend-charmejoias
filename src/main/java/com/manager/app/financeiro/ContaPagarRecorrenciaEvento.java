package com.manager.app.financeiro;

import com.manager.app.usuario.Usuario;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// histórico da regra de recorrência (criação, reajuste, encerramento)
@Entity
@Table(name = "conta_pagar_recorrencia_evento")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
public class ContaPagarRecorrenciaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorrencia_id")
    private ContaPagarRecorrencia recorrencia;

    private String evento;
    private String detalhe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    private LocalDateTime dataHora;
}
