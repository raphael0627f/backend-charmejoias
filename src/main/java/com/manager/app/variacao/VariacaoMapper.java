package com.manager.app.variacao;

import com.manager.app.variacao.dto.ItemVariacaoDTO;
import com.manager.app.variacao.dto.VariacaoDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface VariacaoMapper {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Variacao toEntity(VariacaoDTO dto);

    VariacaoDTO toDTO(Variacao entity);

    // variacao (back-reference) e setado manualmente no service ao associar o item a uma Variacao
    @Mapping(target = "variacao", ignore = true)
    ItemVariacao toEntity(ItemVariacaoDTO dto);
}
