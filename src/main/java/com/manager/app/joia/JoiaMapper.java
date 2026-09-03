package com.manager.app.joia;

import com.manager.app.joia.dto.JoiaRequestDTO;
import com.manager.app.joia.dto.JoiaResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface JoiaMapper {

    Joia toEntity(JoiaRequestDTO dto);

    JoiaRequestDTO toDTO(Joia entity);

    JoiaResponseDTO toResponseDTO(Joia entity);
}
