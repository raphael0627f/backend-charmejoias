package com.manager.app.mapper;

import com.manager.app.dto.GrupoDTO;
import com.manager.app.entity.Grupo;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface GrupoMapper {
    Grupo toEntity(GrupoDTO dto);

    GrupoDTO toDTO (Grupo entity);
}
