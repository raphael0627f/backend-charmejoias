package com.manager.app.comissao;

import com.manager.app.comissao.dto.ComissaoRequestDTO;
import com.manager.app.comissao.dto.ComissaoResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ComissaoMapper {

    Comissao toEntity(ComissaoRequestDTO dto);

    ComissaoResponseDTO toResponseDTO(Comissao entity);
}
