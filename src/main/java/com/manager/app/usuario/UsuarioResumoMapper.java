package com.manager.app.usuario;

import com.manager.app.usuario.dto.UsuarioResumoDTO;
import org.mapstruct.Mapper;

// usado como "uses" pelos mappers de qualquer outro modulo que precise expor
// createdBy/updatedBy (Usuario -> UsuarioResumoDTO) nas respostas
@Mapper(componentModel = "spring")
public interface UsuarioResumoMapper {
    UsuarioResumoDTO toResumoDTO(Usuario usuario);
}
