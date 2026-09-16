package com.manager.app.notafiscal;

import com.manager.app.notafiscal.dto.NotaFiscalCorrecaoResponseDTO;
import com.manager.app.notafiscal.dto.NotaFiscalItemResponseDTO;
import com.manager.app.notafiscal.dto.NotaFiscalPagamentoResponseDTO;
import com.manager.app.notafiscal.dto.NotaFiscalResponseDTO;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = UsuarioResumoMapper.class)
public interface NotaFiscalMapper {

    NotaFiscalResponseDTO toResponseDTO(NotaFiscal entity);

    NotaFiscalItemResponseDTO toItemResponseDTO(NotaFiscalItem entity);

    NotaFiscalPagamentoResponseDTO toPagamentoResponseDTO(NotaFiscalPagamento entity);

    NotaFiscalCorrecaoResponseDTO toCorrecaoResponseDTO(NotaFiscalCorrecao entity);
}
