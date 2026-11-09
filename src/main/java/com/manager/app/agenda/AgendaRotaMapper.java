package com.manager.app.agenda;

import com.manager.app.agenda.dto.AgendaRotaResponseDTO;
import com.manager.app.agenda.dto.AgendaRotaResumoDTO;
import com.manager.app.cidade.CidadeMapper;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = { CidadeMapper.class, UsuarioResumoMapper.class })
public interface AgendaRotaMapper {

    AgendaRotaResponseDTO toResponseDTO(AgendaRota entity);

    AgendaRotaResumoDTO toResumoDTO(AgendaRota entity);
}
