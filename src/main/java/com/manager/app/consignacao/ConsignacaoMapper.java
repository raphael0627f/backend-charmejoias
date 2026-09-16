package com.manager.app.consignacao;

import com.manager.app.consignacao.dto.ConsignacaoRequestDTO;
import com.manager.app.consignacao.dto.ConsignacaoResponseDTO;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = UsuarioResumoMapper.class)
public interface ConsignacaoMapper {

    Consignacao toEntity(ConsignacaoRequestDTO dto);

    ConsignacaoRequestDTO toDTO(Consignacao entity);

    ConsignacaoResponseDTO toResponseDTO(Consignacao entity);
}
