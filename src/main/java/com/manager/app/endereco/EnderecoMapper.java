package com.manager.app.endereco;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EnderecoMapper {

    Endereco toEntity(EnderecoDTO dto);

    EnderecoDTO toDTO(Endereco entity);
}
