package com.manager.app.mostruario;

import com.manager.app.mostruario.dto.MostruarioRequestDTO;
import com.manager.app.mostruario.dto.MostruarioResponseDTO;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = UsuarioResumoMapper.class)
public interface MostruarioMapper {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Mostruario toEntity(MostruarioRequestDTO dto);

    MostruarioResponseDTO toResponseDTO(Mostruario entity);
}
