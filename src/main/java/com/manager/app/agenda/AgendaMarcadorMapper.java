package com.manager.app.agenda;

import com.manager.app.agenda.dto.AgendaMarcadorRequestDTO;
import com.manager.app.agenda.dto.AgendaMarcadorResponseDTO;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = UsuarioResumoMapper.class)
public interface AgendaMarcadorMapper {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    AgendaMarcador toEntity(AgendaMarcadorRequestDTO dto);

    AgendaMarcadorResponseDTO toResponseDTO(AgendaMarcador entity);
}
