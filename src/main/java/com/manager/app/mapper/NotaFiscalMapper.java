package com.manager.app.mapper;

import com.manager.app.dto.notafiscal.NotaFiscalItemResponseDTO;
import com.manager.app.dto.notafiscal.NotaFiscalPagamentoResponseDTO;
import com.manager.app.dto.notafiscal.NotaFiscalResponseDTO;
import com.manager.app.entity.NotaFiscal;
import com.manager.app.entity.NotaFiscalItem;
import com.manager.app.entity.NotaFiscalPagamento;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotaFiscalMapper {

    NotaFiscalResponseDTO toResponseDTO(NotaFiscal entity);

    NotaFiscalItemResponseDTO toItemResponseDTO(NotaFiscalItem entity);

    NotaFiscalPagamentoResponseDTO toPagamentoResponseDTO(NotaFiscalPagamento entity);
}
