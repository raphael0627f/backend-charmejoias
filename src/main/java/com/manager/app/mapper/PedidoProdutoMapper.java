package com.manager.app.mapper;

import com.manager.app.dto.pedido.PedidoProdutoResponseDTO;
import com.manager.app.entity.PedidoProduto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PedidoProdutoMapper {

    @Mapping(source = "produto.id", target = "produtoId")
    @Mapping(source = "produto.codigo", target = "produtoCodigo")
    @Mapping(source = "produto.nome", target = "produtoNome")
    PedidoProdutoResponseDTO toDTO(PedidoProduto entity);
}
