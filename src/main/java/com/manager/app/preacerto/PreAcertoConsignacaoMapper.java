package com.manager.app.preacerto;

import com.manager.app.preacerto.dto.PreAcertoConsignacaoRequestDTO;
import com.manager.app.preacerto.dto.PreAcertoConsignacaoResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PreAcertoConsignacaoMapper {

    PreAcertoConsignacaoRequestDTO toDTO(PreAcertoConsignacao entity);

    @Mapping(source = "consignacao.id", target = "consignacaoId")
    PreAcertoConsignacaoResponseDTO toResponseDTO(PreAcertoConsignacao entity);
}
