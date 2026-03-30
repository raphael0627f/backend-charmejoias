package com.manager.app.mapper;

import com.manager.app.dto.ClienteRequestDTO;
import com.manager.app.dto.ClienteResponseDTO;
import com.manager.app.entity.Cliente;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = EnderecoMapper.class)
public interface ClienteMapper {
    Cliente toEntity(ClienteRequestDTO dto);

    ClienteRequestDTO toDTO(Cliente entity);

    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    ClienteResponseDTO toResponseDTO(Cliente entity);
}
