package com.manager.app.variacao;

import com.manager.app.variacao.dto.VariacaoDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface VariacaoMapper {

    Variacao toEntity(VariacaoDTO dto);

    VariacaoDTO toDTO(Variacao entity);
}
