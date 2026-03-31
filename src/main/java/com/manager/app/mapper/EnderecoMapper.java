package com.manager.app.mapper;

import com.manager.app.dto.endereco.EnderecoDTO;
import com.manager.app.entity.Endereco;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EnderecoMapper {

    Endereco toEntity(EnderecoDTO dto);

    EnderecoDTO toDTO(Endereco entity);
}
