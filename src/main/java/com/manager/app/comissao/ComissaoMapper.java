package com.manager.app.comissao;

import com.manager.app.comissao.dto.ComissaoRequestDTO;
import com.manager.app.comissao.dto.ComissaoResponseDTO;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = UsuarioResumoMapper.class)
public interface ComissaoMapper {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Comissao toEntity(ComissaoRequestDTO dto);

    ComissaoResponseDTO toResponseDTO(Comissao entity);
}
