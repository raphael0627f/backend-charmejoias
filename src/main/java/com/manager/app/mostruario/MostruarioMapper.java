package com.manager.app.mostruario;

import com.manager.app.mostruario.dto.MostruarioRequestDTO;
import com.manager.app.mostruario.dto.MostruarioResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MostruarioMapper {

    Mostruario toEntity(MostruarioRequestDTO dto);

    MostruarioResponseDTO toResponseDTO(Mostruario entity);
}
