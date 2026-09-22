package com.manager.app.joia;

import com.manager.app.joia.dto.JoiaRequestDTO;
import com.manager.app.joia.dto.JoiaResponseDTO;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = UsuarioResumoMapper.class)
public interface JoiaMapper {

    @Mapping(target = "categoria", ignore = true)
    @Mapping(target = "grupos", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Joia toEntity(JoiaRequestDTO dto);

    @Mapping(target = "categoriaId", ignore = true)
    @Mapping(target = "gruposIds", ignore = true)
    JoiaRequestDTO toDTO(Joia entity);

    JoiaResponseDTO toResponseDTO(Joia entity);
}
