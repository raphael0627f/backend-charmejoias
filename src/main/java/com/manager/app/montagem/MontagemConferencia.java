package com.manager.app.montagem;

import com.manager.app.consignacao.Consignacao;
import com.manager.app.consignacao.ConsignacaoProduto;
import com.manager.app.montagem.dto.MontagemConferenciaDTO;
import com.manager.app.montagem.dto.MontagemConferenciaDTO.AvulsoSemVinculo;
import com.manager.app.montagem.dto.MontagemConferenciaDTO.PedidoNaoSeparado;
import com.manager.app.montagem.dto.MontagemConferenciaDTO.ProdutoLinha;
import com.manager.app.pedido.CategoriaPedido;
import com.manager.app.pedido.Pedido;
import com.manager.app.pedido.PedidoProduto;
import com.manager.app.pedido.StatusPedido;
import com.manager.app.produto.Produto;
import com.manager.app.produto.ProdutoVariacao;
import com.manager.app.shared.TipoItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// confere se os produtos dos pedidos da montagem já estão nas consignações vinculadas.
// Só olha pedidos do tipo PRODUTO e não cancelados; itens totalmente cancelados não contam.
// Pedidos de O.S. não têm os produtos conferidos (só precisam estar separados).
// A quantidade é a soma de todas as consignações da montagem (não precisa estar numa só).
@Component
public class MontagemConferencia {

    private static class Acumulado {
        Produto produto;
        ProdutoVariacao variacao;
        int necessario;
        int necessarioSeparado;
        int naConsignacao;
        final Set<Long> pedidosIds = new LinkedHashSet<>();
    }

    private static String chave(UUID produtoId, Long variacaoId){
        return produtoId + ":" + (variacaoId != null ? variacaoId : "");
    }

    private static int disponivel(PedidoProduto item){
        int cancelada = item.getQuantidadeCancelada() != null ? item.getQuantidadeCancelada() : 0;
        return item.getQuantidade() - cancelada;
    }

    public MontagemConferenciaDTO calcular(MontagemMostruario montagem){
        Map<String, Acumulado> acumulados = new LinkedHashMap<>();
        List<AvulsoSemVinculo> avulsos = new ArrayList<>();
        List<PedidoNaoSeparado> naoSeparados = new ArrayList<>();

        for (Pedido pedido : montagem.getPedidos()) {
            if (pedido.getStatus() == StatusPedido.CANCELADO || pedido.getTipo() != TipoItem.PRODUTO) continue;

            boolean separado = pedido.getStatus() == StatusPedido.SEPARADO || pedido.getStatus() == StatusPedido.ENVIADO;
            if (!separado) {
                naoSeparados.add(new PedidoNaoSeparado(pedido.getId(), pedido.getStatus()));
            }

            // pedido de O.S.: os produtos não precisam estar nas consignações (só o pedido precisa estar separado)
            if (pedido.getCategoria() == CategoriaPedido.ORDEM_SERVICO) continue;

            for (PedidoProduto item : pedido.getProdutos()) {
                int disponivel = disponivel(item);
                if (disponivel <= 0) continue;

                if (item.getProduto() == null) {
                    avulsos.add(new AvulsoSemVinculo(pedido.getId(), item.getId(), item.getDescricao(), disponivel));
                    continue;
                }

                Long variacaoId = item.getProdutoVariacao() != null ? item.getProdutoVariacao().getId() : null;
                Acumulado acumulado = acumulados.computeIfAbsent(chave(item.getProduto().getId(), variacaoId), k -> {
                    Acumulado novo = new Acumulado();
                    novo.produto = item.getProduto();
                    novo.variacao = item.getProdutoVariacao();
                    return novo;
                });
                acumulado.necessario += disponivel;
                if (separado) {
                    acumulado.necessarioSeparado += disponivel;
                }
                acumulado.pedidosIds.add(pedido.getId());
            }
        }

        for (Consignacao consignacao : montagem.getConsignacoes()) {
            for (ConsignacaoProduto item : consignacao.getProdutos()) {
                if (item.getProduto() == null) continue;
                Long variacaoId = item.getProdutoVariacao() != null ? item.getProdutoVariacao().getId() : null;
                Acumulado acumulado = acumulados.get(chave(item.getProduto().getId(), variacaoId));
                if (acumulado != null) {
                    acumulado.naConsignacao += item.getQuantidade();
                }
            }
        }

        List<ProdutoLinha> linhas = acumulados.values().stream()
                .map(a -> new ProdutoLinha(
                        a.produto.getId(),
                        a.variacao != null ? a.variacao.getId() : null,
                        a.variacao != null && a.variacao.getSku() != null ? a.variacao.getSku() : a.produto.getSku(),
                        a.produto.getNome(),
                        a.necessario,
                        a.naConsignacao,
                        Math.max(0, a.necessario - a.naConsignacao),
                        Math.max(0, a.necessarioSeparado - a.naConsignacao),
                        new ArrayList<>(a.pedidosIds)))
                .toList();

        boolean ok = avulsos.isEmpty() && naoSeparados.isEmpty() && linhas.stream().allMatch(l -> l.faltando() == 0);
        return new MontagemConferenciaDTO(ok, linhas, avulsos, naoSeparados);
    }
}
