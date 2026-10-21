package com.manager.app.montagem.dto;

import com.manager.app.pedido.StatusPedido;

import java.util.List;
import java.util.UUID;

// conferência da montagem: os produtos dos pedidos vinculados (não cancelados, tipo PRODUTO) precisam
// estar nas consignações vinculadas, e todos esses pedidos precisam estar separados. Calculada a cada
// consulta (não é gravada)
public record MontagemConferenciaDTO(
        boolean ok,
        List<ProdutoLinha> produtos,
        List<AvulsoSemVinculo> avulsosSemVinculo,
        List<PedidoNaoSeparado> pedidosNaoSeparados,
        List<String> instrucoesPendentes) {

    // necessario = soma (qtd - cancelada) nos pedidos; incluido = quanto disso já foi incluído (somado) numa consignação
    // da montagem; faltando = necessario - incluido. Os produtos do pedido são SOMADOS à consignação (consignação com 3
    // + pedido com 4 = 7), por isso o que a consignação já tinha antes não abate o necessário.
    // faltandoSeparados = o que falta considerando só os pedidos já separados - é o que "incluir pendentes" pode
    // colocar na consignação (produto de pedido ainda não separado não entra)
    public record ProdutoLinha(
            UUID produtoId,
            Long produtoVariacaoId,
            String sku,
            String nome,
            String categoriaNome,
            String grupoNome,
            java.math.BigDecimal precoVenda,
            int necessario,
            int incluido,
            int faltando,
            int faltandoSeparados,
            List<Long> pedidosIds) {
    }

    // item avulso (sem produto cadastrado) ainda não vinculado a um produto - o vínculo é feito na separação
    public record AvulsoSemVinculo(Long pedidoId, Long itemId, String descricao, int quantidade) {
    }

    public record PedidoNaoSeparado(Long pedidoId, StatusPedido status) {
    }
}
