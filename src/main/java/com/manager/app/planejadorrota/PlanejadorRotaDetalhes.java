package com.manager.app.planejadorrota;

import com.manager.app.montagem.MontagemConsignacaoProjecao;
import com.manager.app.montagem.MontagemMostruarioRepository;
import com.manager.app.pedido.PedidoProdutoRepository;
import com.manager.app.pedido.PedidoTotaisProjecao;
import com.manager.app.planejadorrota.dto.PlanejadorRotaPedidoResumoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// dados auxiliares da rota buscados em lote (uma consulta para a rota inteira), usados na resposta e no xlsx
@Component
@RequiredArgsConstructor
public class PlanejadorRotaDetalhes {

    private final MontagemMostruarioRepository montagemRepository;
    private final PedidoProdutoRepository pedidoProdutoRepository;

    // consignação -> número da montagem. Se estiver ligada a mais de uma, vale a mais recente (maior id) que não
    // esteja CANCELADA; sem montagem (ou só em montagens canceladas) a consignação não consta no mapa.
    public Map<Long, Long> montagemPorConsignacao(PlanejadorRota rota){
        List<Long> ids = rota.getItens().stream()
                .flatMap(i -> java.util.stream.Stream.concat(
                        i.getConsignacoesDeixar().stream().map(c -> c.getConsignacao().getId()),
                        i.getConsignacoesTrazer().stream().map(c -> c.getConsignacao().getId())))
                .distinct().toList();
        Map<Long, Long> resultado = new HashMap<>();
        if (ids.isEmpty()) return resultado;
        for (MontagemConsignacaoProjecao vinculo : montagemRepository.montagensDasConsignacoes(ids)) {
            resultado.merge(vinculo.getConsignacaoId(), vinculo.getMontagemId(), Math::max);
        }
        return resultado;
    }

    // itens, quantidade e valor dos pedidos (uma consulta so para todos)
    public void preencherTotais(Collection<PlanejadorRotaPedidoResumoDTO> pedidos){
        Map<Long, PedidoTotaisProjecao> totais = totaisDosPedidos(pedidos.stream().map(PlanejadorRotaPedidoResumoDTO::getId).distinct().toList());
        for (PlanejadorRotaPedidoResumoDTO dto : pedidos) {
            PedidoTotaisProjecao t = totais.get(dto.getId());
            dto.setItensCount(t != null ? t.getItensCount().intValue() : 0);
            dto.setQuantidadeTotal(t != null ? t.getQuantidadeTotal() : 0);
            dto.setValorTotal(t != null ? t.getValorTotal() : BigDecimal.ZERO);
        }
    }

    public Map<Long, PedidoTotaisProjecao> totaisDosPedidos(Collection<Long> pedidoIds){
        if (pedidoIds.isEmpty()) return Map.of();
        return pedidoProdutoRepository.buscarTotais(List.copyOf(pedidoIds)).stream()
                .collect(Collectors.toMap(PedidoTotaisProjecao::getPedidoId, Function.identity()));
    }
}
