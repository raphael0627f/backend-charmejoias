package com.manager.app.financeiro;

import com.manager.app.cliente.Cliente;
import com.manager.app.usuario.Usuario;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// lote de crédito do cliente (sem validade). valorUtilizado só cresce pela aplicação em títulos (fase 3); lote desfeito deixa de compor o saldo.
@Entity
@Table(name = "credito_cliente_lote")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
public class CreditoClienteLote {

    public enum Origem { EXCEDENTE_ACERTO, LANCAMENTO_MANUAL }

    public enum Situacao { ATIVO, DESFEITO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    private Origem origemTipo;

    private Long origemId;
    private BigDecimal valorOriginal;
    private BigDecimal valorUtilizado = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private Situacao situacao = Situacao.ATIVO;

    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private Usuario createdBy;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;

    public BigDecimal getDisponivel() {
        return valorOriginal.subtract(valorUtilizado);
    }
}
