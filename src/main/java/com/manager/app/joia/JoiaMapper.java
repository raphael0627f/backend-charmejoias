package com.manager.app.joia;

import com.manager.app.joia.dto.JoiaRequestDTO;
import com.manager.app.joia.dto.JoiaResponseDTO;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = UsuarioResumoMapper.class)
public interface JoiaMapper {

    Joia toEntity(JoiaRequestDTO dto);

    JoiaRequestDTO toDTO(Joia entity);

    JoiaResponseDTO toResponseDTO(Joia entity);
}
