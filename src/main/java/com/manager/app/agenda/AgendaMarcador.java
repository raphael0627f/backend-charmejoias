package com.manager.app.agenda;

import com.manager.app.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.manager.app.cidade.Cidade;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "agenda_marcadores")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgendaMarcador {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate data;

    // só NAO_TRABALHA; no TRABALHA o nome é o da rota (derivado, não armazenado)
    private String nome;

    @Enumerated(EnumType.STRING)
    private AgendaMarcadorTipo tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rota_id")
    private AgendaRota rota;

    // cidades do dia que não fazem parte da rota (só TRABALHA)
    @ManyToMany
    @JoinTable(name = "agenda_marcador_cidades_extras",
            joinColumns = @JoinColumn(name = "marcador_id"),
            inverseJoinColumns = @JoinColumn(name = "cidade_id"))
    @OrderColumn(name = "ordem")
    private List<Cidade> cidadesExtras = new ArrayList<>();

    private LocalDate dataPreAcerto;

    private LocalDate dataAcerto;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private Usuario createdBy;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private Usuario updatedBy;
}
