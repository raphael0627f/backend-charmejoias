package com.manager.app.produto;

import com.manager.app.produto.dto.ProdutoRequestDTO;
import com.manager.app.produto.dto.ProdutoResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProdutoMapper {

    Produto toEntity(ProdutoRequestDTO dto);

    ProdutoRequestDTO toDTO(Produto entity);

    ProdutoResponseDTO toResponseDTO(Produto entity );
}
