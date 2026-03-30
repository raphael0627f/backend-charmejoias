package com.manager.app.mapper;

import com.manager.app.dto.ConsignacaoRequestDTO;
import com.manager.app.dto.ConsignacaoResponseDTO;
import com.manager.app.entity.Consignacao;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConsignacaoMapper {

    Consignacao toEntity(ConsignacaoRequestDTO dto);

    ConsignacaoRequestDTO toDTO(Consignacao entity);

    ConsignacaoResponseDTO toResponseDTO(Consignacao entity);
}
