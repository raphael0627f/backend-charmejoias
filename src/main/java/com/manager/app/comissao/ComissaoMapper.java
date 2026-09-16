package com.manager.app.comissao;

import com.manager.app.comissao.dto.ComissaoRequestDTO;
import com.manager.app.comissao.dto.ComissaoResponseDTO;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = UsuarioResumoMapper.class)
public interface ComissaoMapper {

    Comissao toEntity(ComissaoRequestDTO dto);

    ComissaoResponseDTO toResponseDTO(Comissao entity);
}
