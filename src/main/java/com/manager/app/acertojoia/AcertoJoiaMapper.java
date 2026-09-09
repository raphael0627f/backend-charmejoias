package com.manager.app.acertojoia;

import com.manager.app.acertojoia.dto.AcertoJoiaResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {AcertoJoiaConsignacaoMapper.class})
public interface AcertoJoiaMapper {

    @Mapping(
            target = "valorPagarConsignacaoMaleta",
            expression = "java(entity.valorPagarConsignacoesMaleta())"
    )
    @Mapping(
            target = "valorPagarAcessorios",
            expression = "java(entity.valorPagarAcessorios())"
    )
    AcertoJoiaResponseDTO toResponseDTO(AcertoJoia entity);
}
