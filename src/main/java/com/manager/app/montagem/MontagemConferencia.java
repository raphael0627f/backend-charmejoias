package com.manager.app.montagem;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// confere os produtos dos pedidos da montagem: eles precisam ser INCLUÍDOS (somados) numa consignação da montagem.
// Os produtos do pedido não são "cobertos" pelo que a consignação já tem: consignação com 3 do produto e pedido
// com 4 -> inclui os 4, totalizando 7. O que já foi incluído fica registrado por item de pedido
// (MontagemItemIncluido), por isso a conferência parte do pendente de cada item.
// Só olha pedidos do tipo PRODUTO e não cancelados; itens totalmente cancelados não contam.
// Pedidos de O.S. não têm os produtos conferidos (só precisam estar separados).
@Component
@RequiredArgsConstructor
public class MontagemConferencia {

    private final MontagemItemIncluidoRepository itemIncluidoRepository;

    // um item de pedido (com produto cadastrado) e quanto dele ainda precisa ser incluído numa consignação
    public record ItemPendente(PedidoProduto item, Pedido pedido, int disponivel, int incluido, int pendente, boolean separado) {
    }

    private static class Acumulado {
        Produto produto;
        ProdutoVariacao variacao;
        int necessario;
        int incluido;
        int faltando;
        int faltandoSeparados;
        final Set<Long> pedidosIds = new LinkedHashSet<>();
    }

    private static String chave(UUID produtoId, Long variacaoId){
        return produtoId + ":" + (variacaoId != null ? variacaoId : "");
    }

    private static int disponivel(PedidoProduto item){
        int cancelada = item.getQuantidadeCancelada() != null ? item.getQuantidadeCancelada() : 0;
        return item.getQuantidade() - cancelada;
    }

    private static boolean contaProdutos(Pedido pedido){
        return pedido.getStatus() != StatusPedido.CANCELADO
                && pedido.getTipo() == TipoItem.PRODUTO
                && pedido.getCategoria() != CategoriaPedido.ORDEM_SERVICO;
    }

    private static boolean separado(Pedido pedido){
        return pedido.getStatus() == StatusPedido.SEPARADO || pedido.getStatus() == StatusPedido.ENVIADO;
    }

    // quanto já foi incluído, por item de pedido, nas consignações desta montagem
    private Map<Long, Integer> incluidoPorItem(MontagemMostruario montagem){
        Map<Long, Integer> mapa = new HashMap<>();
        for (MontagemItemIncluido registro : itemIncluidoRepository.findByMontagemId(montagem.getId())) {
            mapa.merge(registro.getPedidoProdutoId(), registro.getQuantidade(), Integer::sum);
        }
        return mapa;
    }

    // itens de pedido com produto cadastrado e a quantidade que ainda falta incluir (disponível - já incluído)
    public List<ItemPendente> itensPendentes(MontagemMostruario montagem){
        Map<Long, Integer> incluidos = incluidoPorItem(montagem);
        List<ItemPendente> itens = new ArrayList<>();
        for (Pedido pedido : montagem.getPedidos()) {
            if (!contaProdutos(pedido)) continue;
            for (PedidoProduto item : pedido.getProdutos()) {
                int disponivel = disponivel(item);
                if (disponivel <= 0 || item.getProduto() == null) continue;
                int incluido = Math.min(incluidos.getOrDefault(item.getId(), 0), disponivel);
                itens.add(new ItemPendente(item, pedido, disponivel, incluido, disponivel - incluido, separado(pedido)));
            }
        }
        return itens;
    }

    public MontagemConferenciaDTO calcular(MontagemMostruario montagem){
        Map<String, Acumulado> acumulados = new LinkedHashMap<>();
        List<AvulsoSemVinculo> avulsos = new ArrayList<>();
        List<PedidoNaoSeparado> naoSeparados = new ArrayList<>();

        for (Pedido pedido : montagem.getPedidos()) {
            if (pedido.getStatus() == StatusPedido.CANCELADO || pedido.getTipo() != TipoItem.PRODUTO) continue;
            if (!separado(pedido)) {
                naoSeparados.add(new PedidoNaoSeparado(pedido.getId(), pedido.getStatus()));
            }
            if (pedido.getCategoria() == CategoriaPedido.ORDEM_SERVICO) continue;

            for (PedidoProduto item : pedido.getProdutos()) {
                int disponivel = disponivel(item);
                if (disponivel > 0 && item.getProduto() == null) {
                    avulsos.add(new AvulsoSemVinculo(pedido.getId(), item.getId(), item.getDescricao(), disponivel));
                }
            }
        }

        for (ItemPendente p : itensPendentes(montagem)) {
            PedidoProduto item = p.item();
            Long variacaoId = item.getProdutoVariacao() != null ? item.getProdutoVariacao().getId() : null;
            Acumulado acumulado = acumulados.computeIfAbsent(chave(item.getProduto().getId(), variacaoId), k -> {
                Acumulado novo = new Acumulado();
                novo.produto = item.getProduto();
                novo.variacao = item.getProdutoVariacao();
                return novo;
            });
            acumulado.necessario += p.disponivel();
            acumulado.incluido += p.incluido();
            acumulado.faltando += p.pendente();
            if (p.separado()) {
                acumulado.faltandoSeparados += p.pendente();
            }
            acumulado.pedidosIds.add(p.pedido().getId());
        }

        List<ProdutoLinha> linhas = acumulados.values().stream()
                .map(a -> new ProdutoLinha(
                        a.produto.getId(),
                        a.variacao != null ? a.variacao.getId() : null,
                        a.variacao != null && a.variacao.getSku() != null ? a.variacao.getSku() : a.produto.getSku(),
                        a.produto.getNome(),
                        a.necessario,
                        a.incluido,
                        a.faltando,
                        a.faltandoSeparados,
                        new ArrayList<>(a.pedidosIds)))
                .toList();

        List<String> instrucoesPendentes = montagem.getInstrucoes().stream()
                .filter(i -> !i.isConcluida())
                .map(MontagemInstrucao::getTexto)
                .toList();

        boolean ok = avulsos.isEmpty() && naoSeparados.isEmpty() && instrucoesPendentes.isEmpty()
                && linhas.stream().allMatch(l -> l.faltando() == 0);
        return new MontagemConferenciaDTO(ok, linhas, avulsos, naoSeparados, instrucoesPendentes);
    }
}
