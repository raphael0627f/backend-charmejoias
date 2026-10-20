package com.manager.app.montagem;

import com.manager.app.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// uma instrução da montagem: o usuário marca como feita quando cumpri-la
@Entity
@Table(name = "montagens_mostruario_instrucoes")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
public class MontagemInstrucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "montagem_id")
    private MontagemMostruario montagem;

    private Integer ordem;

    private String texto;

    private boolean concluida;

    @Column(name = "concluida_em")
    private LocalDateTime concluidaEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concluida_por_id")
    private Usuario concluidaPor;
}
