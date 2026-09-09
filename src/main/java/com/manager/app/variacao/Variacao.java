package com.manager.app.variacao;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "variacoes")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Variacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    private boolean ativo;

    @OneToMany(mappedBy = "variacao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemVariacao> itens = new ArrayList<>();

    @JsonIgnore
    @CreationTimestamp
    private LocalDateTime createdAt;

    @JsonIgnore
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public void adicionarItem(ItemVariacao item) {
        item.setVariacao(this);
        this.itens.add(item);
    }

    public void removerItem(ItemVariacao item) {
        item.setVariacao(null);
        this.itens.remove(item);
    }
}
