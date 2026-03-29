package com.manager.app.mapper;

import com.manager.app.dto.ProdutoDTO;
import com.manager.app.dto.ProdutoResponseDTO;
import com.manager.app.entity.Produto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProdutoMapper {

    Produto toEntity(ProdutoDTO dto);

    ProdutoDTO toDTO(Produto entity);

    ProdutoResponseDTO toResponseDTO(Produto entity );
}
