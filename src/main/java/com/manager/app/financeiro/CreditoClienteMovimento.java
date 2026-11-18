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

// extrato do crédito do cliente: somente inserções (append-only); correções entram como ESTORNO_*
@Entity
@Table(name = "credito_cliente_movimento")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
public class CreditoClienteMovimento {

    public enum Tipo { ENTRADA, SAIDA, ESTORNO_SAIDA, ESTORNO_ENTRADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id")
    private CreditoClienteLote lote;

    @Enumerated(EnumType.STRING)
    private Tipo tipo;

    private BigDecimal valor;
    private Long contaReceberId;
    private Long recebimentoId;
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    private LocalDateTime dataHora = LocalDateTime.now();

    public boolean aumentaSaldo() {
        return tipo == Tipo.ENTRADA || tipo == Tipo.ESTORNO_SAIDA;
    }
}
