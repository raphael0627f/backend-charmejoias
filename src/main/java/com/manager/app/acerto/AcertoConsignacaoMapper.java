package com.manager.app.acerto;

import com.manager.app.acerto.dto.AcertoConsignacaoResponseDTO;
import com.manager.app.consignacao.Consignacao;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AcertoConsignacaoMapper {

    @Mapping(source = "consignacao.id", target = "consignacaoId")
    AcertoConsignacaoResponseDTO toDTO(AcertoConsignacao entity);

    @Mapping(target = "quantidadeProdutos", expression = "java(consignacao.getQuantidadeTotal())")
    @Mapping(target = "valorTotal", expression = "java(consignacao.getValorTotal())")
    @Mapping(target = "consignacao", expression = "java(consignacao)")
    @Mapping(target = "tipo", ignore = true)
    AcertoConsignacao toAcertoConsignacao(Consignacao consignacao);
}