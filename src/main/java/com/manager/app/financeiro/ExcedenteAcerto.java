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

// excedente do acerto de consignação: valor recebido do cliente acima do valor do acerto. Nasce A_DESTINAR; o destino (crédito ou devolução) é escolhido caso a caso.
@Entity
@Table(name = "excedente_acerto")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
public class ExcedenteAcerto {

    public enum Situacao { A_DESTINAR, CREDITO, DEVOLUCAO, CANCELADO }

    public enum Destino { CREDITO, DEVOLUCAO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "acerto_id")
    private com.manager.app.acertoconsignacao.AcertoConsignacao acerto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consignacao_id")
    private com.manager.app.consignacao.Consignacao consignacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    private Situacao situacao = Situacao.A_DESTINAR;

    @Enumerated(EnumType.STRING)
    private Destino destino;

    private LocalDateTime destinadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destinado_por_id")
    private Usuario destinadoPor;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;
}
