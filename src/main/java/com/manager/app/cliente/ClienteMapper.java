package com.manager.app.cliente;

import com.manager.app.cliente.dto.ClienteRequestDTO;
import com.manager.app.cliente.dto.ClienteResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.manager.app.endereco.EnderecoMapper;
import com.manager.app.usuario.UsuarioResumoMapper;

@Mapper(componentModel = "spring", uses = { EnderecoMapper.class, UsuarioResumoMapper.class })
public interface ClienteMapper {
    // grupos e resolvido por id no service; createdBy/updatedBy vem da auditoria automatica
    @Mapping(target = "grupos", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Cliente toEntity(ClienteRequestDTO dto);

    @Mapping(target = "gruposIds", ignore = true)
    ClienteRequestDTO toDTO(Cliente entity);

    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    ClienteResponseDTO toResponseDTO(Cliente entity);
}
