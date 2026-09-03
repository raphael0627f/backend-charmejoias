package com.manager.app.consignacao;

import com.manager.app.consignacao.dto.ConsignacaoRequestDTO;
import com.manager.app.consignacao.dto.ConsignacaoResponseDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConsignacaoMapper {

    Consignacao toEntity(ConsignacaoRequestDTO dto);

    ConsignacaoRequestDTO toDTO(Consignacao entity);

    ConsignacaoResponseDTO toResponseDTO(Consignacao entity);
}
