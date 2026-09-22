package com.manager.app.empresa;

import com.manager.app.empresa.dto.EmpresaRequestDTO;
import com.manager.app.empresa.dto.EmpresaResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.manager.app.endereco.EnderecoMapper;
import com.manager.app.usuario.UsuarioResumoMapper;

@Mapper(componentModel = "spring", uses = { EnderecoMapper.class, UsuarioResumoMapper.class })
public interface EmpresaMapper {

    // contoraCompanyId/certificadoEnviado(Em) sao controlados pelo fluxo de integracao com a
    // Contora, nao pelo request do usuario
    @Mapping(target = "contoraCompanyId", ignore = true)
    @Mapping(target = "certificadoEnviado", ignore = true)
    @Mapping(target = "certificadoEnviadoEm", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Empresa toEntity(EmpresaRequestDTO dto);

    EmpresaResponseDTO toResponseDTO(Empresa entity);
}
