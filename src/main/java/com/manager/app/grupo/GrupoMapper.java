package com.manager.app.grupo;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface GrupoMapper {
    Grupo toEntity(GrupoDTO dto);

    GrupoDTO toDTO (Grupo entity);
}
