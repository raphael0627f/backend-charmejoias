package com.manager.app.produto;

import com.manager.app.produto.dto.ProdutoRequestDTO;
import com.manager.app.produto.dto.ProdutoResponseDTO;
import com.manager.app.produto.dto.ProdutoVariacaoResponseDTO;
import com.manager.app.variacao.ItemVariacao;
import com.manager.app.variacao.dto.ItemVariacaoDTO;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = UsuarioResumoMapper.class)
public interface ProdutoMapper {

    @Mapping(target = "variacoes", ignore = true)
    @Mapping(target = "ncm", ignore = true)
    Produto toEntity(ProdutoRequestDTO dto);

    @Mapping(target = "variacoes", ignore = true)
    @Mapping(target = "ncmCodigo", ignore = true)
    ProdutoRequestDTO toDTO(Produto entity);

    @Mapping(target = "variacoes", ignore = true)
    ProdutoResponseDTO toResponseDTOBase(Produto entity);

    default ProdutoResponseDTO toResponseDTO(Produto entity) {
        ProdutoResponseDTO dto = toResponseDTOBase(entity);
        dto.setVariacoes(entity.getVariacoes().stream().map(this::toVariacaoResponseDTO).toList());
        return dto;
    }

    @Mapping(target = "itensVariacao", ignore = true)
    ProdutoVariacaoResponseDTO toVariacaoResponseDTOBase(ProdutoVariacao entity);

    default ProdutoVariacaoResponseDTO toVariacaoResponseDTO(ProdutoVariacao entity) {
        ProdutoVariacaoResponseDTO dto = toVariacaoResponseDTOBase(entity);
        dto.setItensVariacao(entity.getItens().stream()
                .map(ProdutoVariacaoItem::getItemVariacao)
                .map(this::toItemVariacaoDTO)
                .toList());
        return dto;
    }

    ItemVariacaoDTO toItemVariacaoDTO(ItemVariacao entity);
}
