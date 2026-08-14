package com.manager.app.mapper;

import com.manager.app.dto.acerto.AcertoResponseDTO;
import com.manager.app.entity.Acerto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {AcertoConsignacaoMapper.class})
public interface AcertoMapper {

    @Mapping(
            target = "valorPagarConsignacaoMaleta",
            expression = "java(entity.valorPagarConsignacoesMaleta())"
    )
    @Mapping(
            target = "valorPagarAcessorios",
            expression = "java(entity.valorPagarAcessorios())"
    )
    AcertoResponseDTO toResponseDTO(Acerto entity);
}