package com.manager.app.produto;

import com.manager.app.produto.dto.ProdutoRequestDTO;
import com.manager.app.produto.dto.ProdutoResponseDTO;
import com.manager.app.produto.dto.ProdutoVariacaoResponseDTO;
import com.manager.app.variacao.ItemVariacao;
import com.manager.app.variacao.dto.ItemVariacaoDTO;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", uses = UsuarioResumoMapper.class)
public interface ProdutoMapper {

    @Mapping(target = "variacoes", ignore = true)
    @Mapping(target = "ncm", ignore = true)
    @Mapping(target = "codigoBarras", ignore = true)
    @Mapping(target = "imagemUrl", ignore = true)
    @Mapping(target = "categoria", ignore = true)
    @Mapping(target = "fornecedor", ignore = true)
    @Mapping(target = "grupos", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Produto toEntity(ProdutoRequestDTO dto);

    @Mapping(target = "variacoes", ignore = true)
    @Mapping(target = "ncmCodigo", ignore = true)
    @Mapping(target = "categoriaId", ignore = true)
    @Mapping(target = "fornecedorId", ignore = true)
    @Mapping(target = "gruposIds", ignore = true)
    ProdutoRequestDTO toDTO(Produto entity);

    @Mapping(target = "variacoes", ignore = true)
    ProdutoResponseDTO toResponseDTOBase(Produto entity);

    @Named("produtoToResponseDTO")
    default ProdutoResponseDTO toResponseDTO(Produto entity) {
        if (entity == null) return null;
        ProdutoResponseDTO dto = toResponseDTOBase(entity);
        dto.setVariacoes(entity.getVariacoes().stream().map(this::toVariacaoResponseDTO).toList());
        return dto;
    }

    @Mapping(target = "itensVariacao", ignore = true)
    ProdutoVariacaoResponseDTO toVariacaoResponseDTOBase(ProdutoVariacao entity);

    @Named("produtoVariacaoToResponseDTO")
    default ProdutoVariacaoResponseDTO toVariacaoResponseDTO(ProdutoVariacao entity) {
        if (entity == null) return null;
        ProdutoVariacaoResponseDTO dto = toVariacaoResponseDTOBase(entity);
        dto.setItensVariacao(entity.getItens().stream()
                .map(ProdutoVariacaoItem::getItemVariacao)
                .map(this::toItemVariacaoDTO)
                .toList());
        return dto;
    }

    ItemVariacaoDTO toItemVariacaoDTO(ItemVariacao entity);
}
