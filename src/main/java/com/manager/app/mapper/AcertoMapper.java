package com.manager.app.mapper;

import com.manager.app.dto.acerto.AcertoResponseDTO;
import com.manager.app.entity.Acerto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {AcertoConsignacaoMapper.class})
public interface AcertoMapper {

    @Mapping(target = "ValorPagarConsignacaoMaleta", expression = "java(entity.valorPagarConsignacoesMaleta())")
    @Mapping(target = "ValorPagarAcessorios", expression = "java(entity.valorPagarAcessorios())")
    AcertoResponseDTO toResponseDTO(Acerto entity);
}