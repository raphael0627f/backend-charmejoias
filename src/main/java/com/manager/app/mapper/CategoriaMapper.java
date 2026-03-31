package com.manager.app.mapper;

import com.manager.app.dto.categoria.CategoriaDTO;
import com.manager.app.entity.Categoria;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CategoriaMapper {
    Categoria toEntity(CategoriaDTO dto);

    CategoriaDTO toDTO(Categoria entity);
}
