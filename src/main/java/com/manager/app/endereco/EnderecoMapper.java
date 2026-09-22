package com.manager.app.endereco;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EnderecoMapper {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Endereco toEntity(EnderecoDTO dto);

    EnderecoDTO toDTO(Endereco entity);
}
