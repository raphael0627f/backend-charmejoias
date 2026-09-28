package com.manager.app.consignacao;

import com.manager.app.consignacao.dto.ConsignacaoProdutoRequestDTO;
import com.manager.app.consignacao.dto.ConsignacaoProdutoResponseDTO;
import com.manager.app.consignacao.dto.ConsignacaoRequestDTO;
import com.manager.app.consignacao.dto.ConsignacaoResponseDTO;
import com.manager.app.joia.JoiaMapper;
import com.manager.app.produto.ProdutoMapper;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UsuarioResumoMapper.class, ProdutoMapper.class, JoiaMapper.class})
public interface ConsignacaoMapper {

    // cliente/mostruario sao resolvidos por id no service; confirmada/createdAt/updatedAt/
    // createdBy/updatedBy nao vem do request, sao definidos por regra de negocio ou auditoria
    @Mapping(target = "cliente", ignore = true)
    @Mapping(target = "mostruario", ignore = true)
    @Mapping(target = "confirmada", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Consignacao toEntity(ConsignacaoRequestDTO dto);

    @Mapping(target = "clienteId", ignore = true)
    @Mapping(target = "mostruarioId", ignore = true)
    ConsignacaoRequestDTO toDTO(Consignacao entity);

    ConsignacaoResponseDTO toResponseDTO(Consignacao entity);

    // produto/produtoVariacao/joia sao resolvidos por id no service, id nasce no banco
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "consignacao", ignore = true)
    @Mapping(target = "produto", ignore = true)
    @Mapping(target = "produtoVariacao", ignore = true)
    @Mapping(target = "joia", ignore = true)
    ConsignacaoProduto toEntity(ConsignacaoProdutoRequestDTO dto);

    @Mapping(target = "produtoId", ignore = true)
    @Mapping(target = "produtoVariacaoId", ignore = true)
    @Mapping(target = "joiaId", ignore = true)
    ConsignacaoProdutoRequestDTO toDTO(ConsignacaoProduto entity);

    @Mapping(target = "produto", qualifiedByName = "produtoToResponseDTO")
    @Mapping(target = "produtoVariacao", qualifiedByName = "produtoVariacaoToResponseDTO")
    ConsignacaoProdutoResponseDTO toResponseDTO(ConsignacaoProduto entity);
}
