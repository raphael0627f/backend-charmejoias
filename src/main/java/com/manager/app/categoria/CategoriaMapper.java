package com.manager.app.categoria;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CategoriaMapper {
    Categoria toEntity(CategoriaDTO dto);

    CategoriaDTO toDTO(Categoria entity);
}
