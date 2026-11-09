package com.manager.app.agenda;

import com.manager.app.agenda.dto.AgendaMarcadorRequestDTO;
import com.manager.app.agenda.dto.AgendaMarcadorResponseDTO;
import com.manager.app.usuario.UsuarioResumoMapper;
import com.manager.app.cidade.CidadeMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = { UsuarioResumoMapper.class, AgendaRotaMapper.class, CidadeMapper.class })
public interface AgendaMarcadorMapper {

    @Mapping(target = "rota", ignore = true)
    @Mapping(target = "cidadesExtras", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    AgendaMarcador toEntity(AgendaMarcadorRequestDTO dto);

    // no TRABALHA o nome é o da rota (derivado, não armazenado)
    @Mapping(target = "nome", expression = "java(nomeDe(entity))")
    AgendaMarcadorResponseDTO toResponseDTO(AgendaMarcador entity);

    default String nomeDe(AgendaMarcador marcador){
        return marcador.getRota() != null ? marcador.getRota().getNome() : marcador.getNome();
    }
}
