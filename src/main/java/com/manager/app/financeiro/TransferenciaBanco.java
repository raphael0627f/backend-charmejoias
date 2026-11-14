package com.manager.app.financeiro;

import com.manager.app.usuario.Usuario;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// uma transferência gera duas movimentações ligadas (saída na origem, entrada no destino)
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "transferencia_banco")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
public class TransferenciaBanco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "banco_origem_id")
    private Banco bancoOrigem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "banco_destino_id")
    private Banco bancoDestino;

    private BigDecimal valor;
    private LocalDate data;
    private String descricao;
    private boolean estornada;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private Usuario createdBy;
}
