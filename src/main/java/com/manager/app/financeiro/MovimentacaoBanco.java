package com.manager.app.financeiro;

import com.manager.app.usuario.Usuario;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// lançamento no extrato de um banco; nunca é apagado nem alterado: o estorno cria a movimentação inversa
@Entity
@Table(name = "movimentacao_banco")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
public class MovimentacaoBanco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "banco_id")
    private Banco banco;

    private LocalDate data;

    @Enumerated(EnumType.STRING)
    private TipoMovimentacao tipo;

    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    private OrigemMovimentacao origem;

    private String descricao;

    // reservado para o pagamento de conta a pagar (fase 2); ainda sem chave estrangeira
    private Long pagamentoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transferencia_id")
    private TransferenciaBanco transferencia;

    private boolean estornada;

    // preenchido na movimentação inversa gerada por um estorno
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estorno_de_id")
    private MovimentacaoBanco estornoDe;

    private String motivoEstorno;
    private LocalDateTime estornadaEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estornada_por_id")
    private Usuario estornadaPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    private LocalDateTime criadoEm;

    @PrePersist
    void aoCriar() {
        if (criadoEm == null) criadoEm = LocalDateTime.now();
    }
}
