package com.manager.app.rota;

import com.manager.app.cliente.Cliente;
import com.manager.app.endereco.Endereco;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rota_clientes")
@EqualsAndHashCode(of = "id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RotaCliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rota_id", nullable = false)
    private Rota rota;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    // endereço escolhido pelo usuário entre os cadastrados do cliente, usado pra
    // latitude/longitude - a coluna "Endereço" do xlsx exportado traz todos os
    // endereços do cliente, começando por este
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endereco_id", nullable = false)
    private Endereco endereco;

    private Integer ordem;

    private String observacao;

    // informado pelo frontend (nao calculado aqui) - quanto deve ser cobrado/recebido
    // desse cliente nessa parada da rota. Nome de coluna explicito porque o "AP" maiusculo
    // consecutivo confunde a conversao automatica pra snake_case (viraria "valorapagar")
    @Column(name = "valor_a_pagar", precision = 10, scale = 2)
    private BigDecimal valorAPagar;

    // pode ter mais de uma consignação a deixar/trazer por cliente - a confirmação de
    // entrega/coleta é registrada por consignação (RotaConsignacaoDeixar/Trazer.confirmada),
    // não agregada aqui, senão perde-se qual item especifico nao foi entregue/recolhido
    @OneToMany(mappedBy = "rotaCliente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RotaConsignacaoDeixar> consignacoesDeixar = new ArrayList<>();

    @OneToMany(mappedBy = "rotaCliente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RotaConsignacaoTrazer> consignacoesTrazer = new ArrayList<>();

    public void adicionarConsignacaoDeixar(RotaConsignacaoDeixar item) {
        item.setRotaCliente(this);
        this.consignacoesDeixar.add(item);
    }

    public void removerConsignacaoDeixar(RotaConsignacaoDeixar item) {
        item.setRotaCliente(null);
        this.consignacoesDeixar.remove(item);
    }

    public void adicionarConsignacaoTrazer(RotaConsignacaoTrazer item) {
        item.setRotaCliente(this);
        this.consignacoesTrazer.add(item);
    }

    public void removerConsignacaoTrazer(RotaConsignacaoTrazer item) {
        item.setRotaCliente(null);
        this.consignacoesTrazer.remove(item);
    }
}
