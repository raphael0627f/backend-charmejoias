package com.manager.app.mapper;

import com.manager.app.dto.acerto.AcertoResponseDTO;
import com.manager.app.entity.Acerto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {AcertoConsignacaoMapper.class})
public interface AcertoMapper {

    AcertoResponseDTO toResponseDTO(Acerto entity);
}