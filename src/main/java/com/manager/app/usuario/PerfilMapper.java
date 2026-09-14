package com.manager.app.usuario;

import com.manager.app.usuario.dto.PerfilRequestDTO;
import com.manager.app.usuario.dto.PerfilResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PerfilMapper {

    Perfil toEntity(PerfilRequestDTO dto);

    PerfilResponseDTO toResponseDTO(Perfil entity);
}
