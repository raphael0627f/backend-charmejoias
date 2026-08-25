package com.manager.app.mapper;

import com.manager.app.dto.joia.JoiaRequestDTO;
import com.manager.app.dto.joia.JoiaResponseDTO;
import com.manager.app.entity.Joia;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface JoiaMapper {

    Joia toEntity(JoiaRequestDTO dto);

    JoiaRequestDTO toDTO(Joia entity);

    JoiaResponseDTO toResponseDTO(Joia entity);
}
