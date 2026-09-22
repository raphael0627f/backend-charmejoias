package com.manager.app.grupo;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GrupoMapper {
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Grupo toEntity(GrupoDTO dto);

    GrupoDTO toDTO (Grupo entity);
}
