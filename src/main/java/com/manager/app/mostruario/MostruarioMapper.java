package com.manager.app.mostruario;

import com.manager.app.mostruario.dto.MostruarioRequestDTO;
import com.manager.app.mostruario.dto.MostruarioResponseDTO;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = UsuarioResumoMapper.class)
public interface MostruarioMapper {

    Mostruario toEntity(MostruarioRequestDTO dto);

    MostruarioResponseDTO toResponseDTO(Mostruario entity);
}
