package com.manager.app.financeiro;

import com.manager.app.usuario.Usuario;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// histórico do título a receber (criação, edição, alteração de vencimento, cancelamento...)
@Entity
@Table(name = "conta_receber_evento")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
public class ContaReceberEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_receber_id")
    private ContaReceber contaReceber;

    private String evento;
    private String detalhe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    private LocalDateTime dataHora;
}
