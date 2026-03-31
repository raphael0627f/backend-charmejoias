package com.manager.app.mapper;

import com.manager.app.dto.pedido.PedidoResponseDTO;
import com.manager.app.entity.Pedido;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring", uses = {PedidoProdutoMapper.class})
public interface PedidoMapper {

    @Mapping(source = "cliente.id", target = "clienteId")
    @Mapping(source = "cliente.codigo", target = "clienteCodigo")
    @Mapping(source = "cliente.nome", target = "clienteNome")
    @Mapping(source = "produtos", target = "produtosResponse")
    PedidoResponseDTO toResponseDTO(Pedido entity);
}
