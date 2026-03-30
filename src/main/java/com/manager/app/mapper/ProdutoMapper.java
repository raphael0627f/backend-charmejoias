package com.manager.app.mapper;

import com.manager.app.dto.ProdutoRequestDTO;
import com.manager.app.dto.ProdutoResponseDTO;
import com.manager.app.entity.Produto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProdutoMapper {

    Produto toEntity(ProdutoRequestDTO dto);

    ProdutoRequestDTO toDTO(Produto entity);

    ProdutoResponseDTO toResponseDTO(Produto entity );
}
