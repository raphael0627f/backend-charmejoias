package com.manager.app.preacerto;

import com.manager.app.preacerto.dto.PreAcertoConsignacaoProdutoRequestDTO;
import com.manager.app.preacerto.dto.PreAcertoConsignacaoProdutoResponseDTO;
import com.manager.app.preacerto.dto.PreAcertoConsignacaoRequestDTO;
import com.manager.app.preacerto.dto.PreAcertoConsignacaoResponseDTO;
import com.manager.app.joia.JoiaMapper;
import com.manager.app.produto.ProdutoMapper;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UsuarioResumoMapper.class, ProdutoMapper.class, JoiaMapper.class})
public interface PreAcertoConsignacaoMapper {

    @Mapping(source = "consignacao.id", target = "consignacaoId")
    @Mapping(source = "cliente.id", target = "clienteId")
    PreAcertoConsignacaoRequestDTO toDTO(PreAcertoConsignacao entity);

    @Mapping(source = "consignacao.id", target = "consignacaoId")
    PreAcertoConsignacaoResponseDTO toResponseDTO(PreAcertoConsignacao entity);

    @Mapping(target = "produtoId", ignore = true)
    @Mapping(target = "produtoVariacaoId", ignore = true)
    @Mapping(target = "joiaId", ignore = true)
    PreAcertoConsignacaoProdutoRequestDTO toDTO(PreAcertoConsignacaoProduto entity);

    @Mapping(target = "produto", qualifiedByName = "produtoToResponseDTO")
    @Mapping(target = "produtoVariacao", qualifiedByName = "produtoVariacaoToResponseDTO")
    PreAcertoConsignacaoProdutoResponseDTO toResponseDTO(PreAcertoConsignacaoProduto entity);
}
