package com.manager.app.pedido;

import com.manager.app.shared.ModoBusca;

import java.time.LocalDate;
import java.util.List;

public record PedidoFiltro(
        String busca,
        ModoBusca modoBusca,
        List<StatusPedido> status,
        List<CategoriaPedido> categoria,
        Long numero,
        String clienteCodigo,
        String clienteNome,
        String cidade,
        LocalDate dataEntregaInicio,
        LocalDate dataEntregaFim,
        LocalDate criadoInicio,
        LocalDate criadoFim,
        // true = só pedidos com cliente, false = só pedidos sem cliente (avulsos), null = todos
        Boolean temCliente,
        // true = só pedidos que não constam em nenhuma montagem (qualquer status da montagem: o vínculo só some ao desvincular)
        Boolean semMontagem
) {
    // as contagens por status/categoria ignoram o próprio filtro, senão o total dos outros
    // status/categorias sempre sairia zerado quando o usuário filtra um deles
    public PedidoFiltro semStatus(){
        return new PedidoFiltro(busca, modoBusca, null, categoria, numero, clienteCodigo, clienteNome, cidade,
                dataEntregaInicio, dataEntregaFim, criadoInicio, criadoFim, temCliente, semMontagem);
    }

    public PedidoFiltro semCategoria(){
        return new PedidoFiltro(busca, modoBusca, status, null, numero, clienteCodigo, clienteNome, cidade,
                dataEntregaInicio, dataEntregaFim, criadoInicio, criadoFim, temCliente, semMontagem);
    }
}
