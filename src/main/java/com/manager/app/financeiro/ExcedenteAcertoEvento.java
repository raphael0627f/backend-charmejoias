package com.manager.app.financeiro;

import com.manager.app.usuario.Usuario;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// histórico do excedente do acerto (criação, ajuste, cancelamento; destino e desfazer nas fases seguintes)
@Entity
@Table(name = "excedente_acerto_evento")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
public class ExcedenteAcertoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "excedente_id")
    private ExcedenteAcerto excedente;

    private String evento;
    private String detalhe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    private LocalDateTime dataHora;
}
