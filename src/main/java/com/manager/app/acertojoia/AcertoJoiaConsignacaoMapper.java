package com.manager.app.acertojoia;

import com.manager.app.acertojoia.dto.AcertoJoiaConsignacaoResponseDTO;
import com.manager.app.consignacao.Consignacao;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AcertoJoiaConsignacaoMapper {

    @Mapping(source = "consignacao.id", target = "consignacaoId")
    AcertoJoiaConsignacaoResponseDTO toDTO(AcertoJoiaConsignacao entity);

    @Mapping(target = "quantidadeProdutos", expression = "java(consignacao.getQuantidadeTotal())")
    @Mapping(target = "valorTotal", expression = "java(consignacao.getValorTotal())")
    @Mapping(target = "consignacao", expression = "java(consignacao)")
    @Mapping(target = "tipo", ignore = true)
    @Mapping(target = "acertoJoia", ignore = true)
    AcertoJoiaConsignacao toAcertoJoiaConsignacao(Consignacao consignacao);
}
