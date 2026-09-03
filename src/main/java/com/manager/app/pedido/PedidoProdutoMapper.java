package com.manager.app.pedido;

import com.manager.app.pedido.dto.PedidoProdutoResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PedidoProdutoMapper {

    @Mapping(source = "produto.id", target = "produtoId")
    @Mapping(source = "produto.codigo", target = "produtoCodigo")
    @Mapping(source = "produto.nome", target = "produtoNome")
    @Mapping(source = "joia.id", target = "joiaId")
    @Mapping(source = "joia.codigo", target = "joiaCodigo")
    @Mapping(source = "joia.nome", target = "joiaNome")
    PedidoProdutoResponseDTO toDTO(PedidoProduto entity);
}
