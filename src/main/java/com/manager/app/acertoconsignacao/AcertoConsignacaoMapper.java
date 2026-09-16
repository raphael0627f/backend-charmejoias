package com.manager.app.acertoconsignacao;

import com.manager.app.acertoconsignacao.dto.AcertoConsignacaoRequestDTO;
import com.manager.app.acertoconsignacao.dto.AcertoConsignacaoResponseDTO;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = UsuarioResumoMapper.class)
public interface AcertoConsignacaoMapper {

    AcertoConsignacaoRequestDTO toDTO(AcertoConsignacao entity);

    @Mapping(source = "consignacao.id", target = "consignacaoId")
    @Mapping(source = "preAcerto.id", target = "preAcertoId")
    @Mapping(target = "quantidadeProdutosAcertados", expression = "java(entity.getQuantidadeProdutosAcertados())")
    @Mapping(target = "quantidadeProdutosDevolvidos", expression = "java(entity.getQuantidadeProdutosDevolvidos())")
    @Mapping(target = "valorTotalProdutosAcertados", expression = "java(entity.getValorTotalProdutosAcertados())")
    @Mapping(target = "valorTotalComDesconto", expression = "java(entity.getValorTotalComDesconto())")
    AcertoConsignacaoResponseDTO toResponseDTO(AcertoConsignacao entity);
}
