package com.manager.app.montagem;

import com.manager.app.montagem.dto.MontagemResponseDTO;
import com.manager.app.mostruario.MostruarioMapper;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UsuarioResumoMapper.class, MostruarioMapper.class})
public interface MontagemMostruarioMapper {

    @Mapping(target = "clienteId", source = "cliente.id")
    MontagemResponseDTO toResponseDTO(MontagemMostruario entity);
}
