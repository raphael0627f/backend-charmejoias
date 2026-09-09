package com.manager.app.pedido;

import com.manager.app.pedido.dto.PedidoResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring", uses = {PedidoProdutoMapper.class})
public interface PedidoMapper {

    @Mapping(source = "cliente.id", target = "clienteId")
    @Mapping(source = "cliente.codigo", target = "clienteCodigo")
    @Mapping(source = "cliente.nome", target = "clienteNome")
    @Mapping(source = "produtos", target = "produtosResponse", qualifiedByName = "pedidoProdutoToDTO")
    PedidoResponseDTO toResponseDTO(Pedido entity);
}
