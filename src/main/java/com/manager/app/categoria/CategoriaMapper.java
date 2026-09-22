package com.manager.app.categoria;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoriaMapper {
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Categoria toEntity(CategoriaDTO dto);

    CategoriaDTO toDTO(Categoria entity);
}
