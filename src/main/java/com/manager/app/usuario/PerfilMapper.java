package com.manager.app.usuario;

import com.manager.app.usuario.dto.PerfilRequestDTO;
import com.manager.app.usuario.dto.PerfilResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = UsuarioResumoMapper.class)
public interface PerfilMapper {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Perfil toEntity(PerfilRequestDTO dto);

    PerfilResponseDTO toResponseDTO(Perfil entity);
}
