package com.manager.app.pedido;

import com.manager.app.pedido.dto.PedidoProdutoResponseDTO;
import com.manager.app.variacao.ItemVariacao;
import com.manager.app.variacao.dto.ItemVariacaoDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface PedidoProdutoMapper {

    @Named("pedidoProdutoToDTOBase")
    @Mapping(source = "produto.id", target = "produtoId")
    @Mapping(source = "produto.sku", target = "produtoCodigo")
    @Mapping(source = "produto.nome", target = "produtoNome")
    @Mapping(source = "produtoVariacao.id", target = "produtoVariacaoId")
    @Mapping(source = "produtoVariacao.sku", target = "produtoVariacaoSku")
    @Mapping(target = "produtoVariacaoItens", ignore = true)
    @Mapping(source = "joia.id", target = "joiaId")
    @Mapping(source = "joia.codigo", target = "joiaCodigo")
    @Mapping(source = "joia.nome", target = "joiaNome")
    PedidoProdutoResponseDTO toDTOBase(PedidoProduto entity);

    @Named("pedidoProdutoToDTO")
    default PedidoProdutoResponseDTO toDTO(PedidoProduto entity) {
        PedidoProdutoResponseDTO dto = toDTOBase(entity);
        if (entity.getProdutoVariacao() != null) {
            dto.setProdutoVariacaoItens(entity.getProdutoVariacao().getItens().stream()
                    .map(item -> toItemVariacaoDTO(item.getItemVariacao()))
                    .toList());
        }
        return dto;
    }

    ItemVariacaoDTO toItemVariacaoDTO(ItemVariacao entity);
}
