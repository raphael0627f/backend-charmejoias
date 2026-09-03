package com.manager.app.acerto;

import com.manager.app.acerto.dto.AcertoResponseDTO;
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