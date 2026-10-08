package com.manager.app.pedido;

import com.manager.app.grupo.TipoGrupo;
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
    @Mapping(source = "grupo.id", target = "grupoId")
    PedidoProdutoResponseDTO toDTOBase(PedidoProduto entity);

    @Named("pedidoProdutoToDTO")
    default PedidoProdutoResponseDTO toDTO(PedidoProduto entity) {
        PedidoProdutoResponseDTO dto = toDTOBase(entity);
        if (entity.getProdutoVariacao() != null) {
            dto.setProdutoVariacaoItens(entity.getProdutoVariacao().getItens().stream()
                    .map(item -> toItemVariacaoDTO(item.getItemVariacao()))
                    .toList());
        }
        dto.setGrupoNome(grupoNomeDe(entity));
        dto.setCategoriaNome(categoriaNomeDe(entity));
        if (entity.getProdutoVariacao() != null && entity.getProdutoVariacao().getCodigoBarras() != null) {
            dto.setCodigoBarras(entity.getProdutoVariacao().getCodigoBarras());
        } else if (entity.getProduto() != null) {
            dto.setCodigoBarras(entity.getProduto().getCodigoBarras());
        }
        if (entity.getProduto() != null) {
            dto.setReferencia(entity.getProduto().getReferencia());
        } else if (entity.getJoia() != null) {
            dto.setReferencia(entity.getJoia().getReferencia());
        }
        return dto;
    }

    private String categoriaNomeDe(PedidoProduto entity) {
        if (entity.getProduto() != null && entity.getProduto().getCategoria() != null) {
            return entity.getProduto().getCategoria().getNome();
        }
        if (entity.getJoia() != null && entity.getJoia().getCategoria() != null) {
            return entity.getJoia().getCategoria().getNome();
        }
        return null;
    }

    // produto/joia cadastrado tem precedência (o grupo é do catálogo, podendo ter
    // mais de um - junta os nomes); senão usa o grupo escolhido manualmente no
    // item avulso
    private String grupoNomeDe(PedidoProduto entity) {
        if (entity.getProduto() != null && entity.getProduto().getGrupos() != null) {
            String nomes = nomesDosGruposDeProduto(entity.getProduto().getGrupos());
            if (!nomes.isEmpty()) return nomes;
        }
        if (entity.getJoia() != null && entity.getJoia().getGrupos() != null) {
            String nomes = nomesDosGruposDeProduto(entity.getJoia().getGrupos());
            if (!nomes.isEmpty()) return nomes;
        }
        return entity.getGrupo() != null ? entity.getGrupo().getNome() : null;
    }

    private String nomesDosGruposDeProduto(java.util.List<com.manager.app.grupo.Grupo> grupos) {
        return grupos.stream()
                .filter(g -> g.getTipo() == TipoGrupo.PRODUTO)
                .map(com.manager.app.grupo.Grupo::getNome)
                .reduce((a, b) -> a + " / " + b)
                .orElse("");
    }

    ItemVariacaoDTO toItemVariacaoDTO(ItemVariacao entity);
}
